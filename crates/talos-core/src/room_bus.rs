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
