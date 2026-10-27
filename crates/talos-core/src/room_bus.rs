use crate::models::{BusEvent, EventType, Message};
use std::collections::HashMap;
use std::sync::{Arc, Mutex as StdMutex};
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::sync::{broadcast, Mutex as AsyncMutex};
use uuid::Uuid;

fn now_ts() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs_f64()
}

pub struct RoomChannel {
    pub sender: broadcast::Sender<String>,
    pub turn_lock: Arc<AsyncMutex<()>>,
}

#[derive(Clone)]
pub struct RoomBus {
    rooms: Arc<StdMutex<HashMap<String, Arc<RoomChannel>>>>,
}

impl RoomBus {
    pub fn new() -> Self {
        Self {
            rooms: Arc::new(StdMutex::new(HashMap::new())),
        }
    }

    pub fn get_or_create_channel(&self, room_id: &str) -> Arc<RoomChannel> {
        let mut map = self.rooms.lock().unwrap();
        if let Some(ch) = map.get(room_id) {
            return ch.clone();
        }
        let (tx, _rx) = broadcast::channel(128);
        let ch = Arc::new(RoomChannel {
            sender: tx,
            turn_lock: Arc::new(AsyncMutex::new(())),
        });
        map.insert(room_id.to_string(), ch.clone());
        ch
    }

    pub fn broadcast_message(&self, msg: &Message) {
        let channel = self.get_or_create_channel(&msg.room_id);
        let event = BusEvent {
            event_id: Uuid::new_v4().to_string(),
            event_type: EventType::MessageNew,
            room_id: msg.room_id.clone(),
            payload: serde_json::to_value(msg).unwrap_or_default(),
            timestamp: now_ts(),
        };

        if let Ok(json_str) = serde_json::to_string(&event) {
            let _ = channel.sender.send(json_str);
        }
    }

    pub fn broadcast_typing(&self, room_id: &str, sender_id: &str, is_typing: bool) {
        let channel = self.get_or_create_channel(room_id);
        let event = BusEvent {
            event_id: Uuid::new_v4().to_string(),
            event_type: EventType::TypingIndicator,
            room_id: room_id.to_string(),
            payload: serde_json::json!({
                "sender_id": sender_id,
                "is_typing": is_typing,
            }),
            timestamp: now_ts(),
        };

        if let Ok(json_str) = serde_json::to_string(&event) {
            let _ = channel.sender.send(json_str);
        }
    }
}


/// Inbound Media Batcher: aggregates rapid multi-image submissions (bursts/albums)
/// so AI agents receive and analyze all images in ONE unified turn instead of one-by-one.
pub struct MediaBatchEntry {
    pub room_id: String,
    pub sender_id: String,
    pub sender_name: String,
    pub text: String,
    pub attachments: Vec<String>,
    pub timer_handle: Option<tokio::task::JoinHandle<()>>,
}

#[derive(Clone)]
pub struct MediaBatchQueue {
    pending: Arc<AsyncMutex<HashMap<String, Arc<AsyncMutex<MediaBatchEntry>>>>>,
}

impl MediaBatchQueue {
    pub fn new() -> Self {
        Self {
            pending: Arc::new(AsyncMutex::new(HashMap::new())),
        }
    }

    /// Enqueue an incoming message with attachments.
    /// Debounces across a 3.0 second window to collect all images before broadcasting.
    pub async fn enqueue_or_merge(
        &self,
        room_bus: Arc<RoomBus>,
        room_id: String,
        sender_id: String,
        sender_name: String,
        text: String,
        new_attachments: Vec<String>,
    ) {
        let key = format!("{}:{}", room_id, sender_id);
        let mut map = self.pending.lock().await;

        if let Some(entry_lock) = map.get(&key) {
            let mut entry = entry_lock.lock().await;
            entry.attachments.extend(new_attachments);
            if !text.is_empty() && !entry.text.contains(&text) {
                if entry.text.is_empty() {
                    entry.text = text;
                } else {
                    entry.text.push_str(" ");
                    entry.text.push_str(&text);
                }
            }
            // Reset debounce timer
            if let Some(handle) = entry.timer_handle.take() {
                handle.abort();
            }
            let queue_clone = self.clone();
            let key_clone = key.clone();
            let bus_clone = room_bus.clone();
            let entry_clone = entry_lock.clone();

            entry.timer_handle = Some(tokio::spawn(async move {
                tokio::time::sleep(tokio::time::Duration::from_millis(3000)).await;
                queue_clone.flush(&key_clone, bus_clone, entry_clone).await;
            }));
        } else {
            let entry = Arc::new(AsyncMutex::new(MediaBatchEntry {
                room_id: room_id.clone(),
                sender_id: sender_id.clone(),
                sender_name: sender_name.clone(),
                text: text.clone(),
                attachments: new_attachments,
                timer_handle: None,
            }));

            let queue_clone = self.clone();
            let key_clone = key.clone();
            let bus_clone = room_bus.clone();
            let entry_clone = entry.clone();

            {
                let mut e = entry.lock().await;
                e.timer_handle = Some(tokio::spawn(async move {
                    tokio::time::sleep(tokio::time::Duration::from_millis(3000)).await;
                    queue_clone.flush(&key_clone, bus_clone, entry_clone).await;
                }));
            }

            map.insert(key, entry);
        }
    }

    async fn flush(
        &self,
        key: &str,
        room_bus: Arc<RoomBus>,
        entry_lock: Arc<AsyncMutex<MediaBatchEntry>>,
    ) {
        // Remove from map
        {
            let mut map = self.pending.lock().await;
            map.remove(key);
        }

        let entry = entry_lock.lock().await;
        let msg = Message {
            id: Uuid::new_v4().to_string(),
            room_id: entry.room_id.clone(),
            sender_id: entry.sender_id.clone(),
            sender_name: entry.sender_name.clone(),
            sender_role: crate::models::Role::Human,
            content: entry.text.clone(),
            thought: None,
            tool_calls: Vec::new(),
            attachments: entry.attachments.clone(),
            reply_to_id: None,
            created_at: now_ts(),
        };

        room_bus.broadcast_message(&msg);
    }
}
