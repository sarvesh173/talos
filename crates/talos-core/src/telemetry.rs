use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::time::{Duration, Instant};

/// Warning status thresholds for context saturation.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SaturationThreshold {
    /// Context usage is below 80% (< 80.0%).
    Normal,
    /// Context usage is between 80% and 90% (80.0% ..= 90.0%).
    Warning,
    /// Context usage is above 90% (> 90.0%).
    Critical,
}

/// Context Window Saturation tracking current consumption vs maximum model limit.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ContextSaturation {
    pub prompt_tokens: u64,
    pub completion_tokens: u64,
    pub total_tokens: u64,
    pub context_window_limit: u64,
    pub usage_pct: f32,
}

impl ContextSaturation {
    /// Creates a new `ContextSaturation` instance and calculates usage percentage.
    pub fn new(prompt_tokens: u64, completion_tokens: u64, context_window_limit: u64) -> Self {
        let total_tokens = prompt_tokens.saturating_add(completion_tokens);
        let usage_pct = if context_window_limit == 0 {
            0.0
        } else {
            ((total_tokens as f64 / context_window_limit as f64) * 100.0) as f32
        };

        Self {
            prompt_tokens,
            completion_tokens,
            total_tokens,
            context_window_limit,
            usage_pct,
        }
    }

    /// Evaluates threshold warning level based on `usage_pct`:
    /// - Normal: < 80.0%
    /// - Warning: 80.0% ..= 90.0%
    /// - Critical: > 90.0%
    pub fn threshold_warning(&self) -> SaturationThreshold {
        if self.usage_pct > 90.0 {
            SaturationThreshold::Critical
        } else if self.usage_pct >= 80.0 {
            SaturationThreshold::Warning
        } else {
            SaturationThreshold::Normal
        }
    }
}

/// Configuration settings for telemetry sync behavior.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct TelemetryConfig {
    /// Sync delay in seconds:
    /// - 0 for realtime mode
    /// - 1..=10 for optimized delay mode (default: 5)
    pub sync_delay_secs: u64,
}

impl Default for TelemetryConfig {
    fn default() -> Self {
        Self { sync_delay_secs: 5 }
    }
}

impl TelemetryConfig {
    pub fn new(sync_delay_secs: u64) -> Self {
        Self {
            sync_delay_secs: sync_delay_secs.clamp(0, 10),
        }
    }

    pub fn is_realtime(&self) -> bool {
        self.sync_delay_secs == 0
    }
}

/// Room Context Breakdown for diagnostics sheet and interactive corner ring.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RoomContextBreakdown {
    pub system_tokens: u64,
    pub history_tokens: u64,
    pub draft_tokens: u64,
    pub completion_tokens: u64,
    pub free_tokens: u64,
    pub max_window: u64,
}

impl RoomContextBreakdown {
    /// Constructs breakdown and calculates remaining free tokens safely.
    pub fn new(
        system_tokens: u64,
        history_tokens: u64,
        draft_tokens: u64,
        completion_tokens: u64,
        max_window: u64,
    ) -> Self {
        let used = system_tokens
            .saturating_add(history_tokens)
            .saturating_add(draft_tokens)
            .saturating_add(completion_tokens);
        let free_tokens = max_window.saturating_sub(used);

        Self {
            system_tokens,
            history_tokens,
            draft_tokens,
            completion_tokens,
            free_tokens,
            max_window,
        }
    }
}

/// Timestamped sample of token counts for sliding window velocity calculations.
#[derive(Debug, Clone, Copy)]
struct TokenSample {
    timestamp: Instant,
    tokens: u64,
}

/// Tracks timestamped token increments to compute moving average generation velocity.
#[derive(Debug, Clone)]
pub struct TokenVelocityTracker {
    window_duration: Duration,
    samples: VecDeque<TokenSample>,
}

impl TokenVelocityTracker {
    /// Creates a tracker with a default 10-second moving window.
    pub fn new() -> Self {
        Self::with_window(Duration::from_secs(10))
    }

    /// Creates a tracker with a custom window duration.
    pub fn with_window(window_duration: Duration) -> Self {
        Self {
            window_duration,
            samples: VecDeque::new(),
        }
    }

    /// Record a token increment at the current instant.
    pub fn record_tokens(&mut self, tokens: u64) {
        self.record_tokens_at(tokens, Instant::now());
    }

    /// Record a token increment at a specific timestamp.
    pub fn record_tokens_at(&mut self, tokens: u64, timestamp: Instant) {
        self.samples.push_back(TokenSample { timestamp, tokens });
        self.prune(timestamp);
    }

    /// Prunes samples older than the moving average window duration.
    fn prune(&mut self, current_time: Instant) {
        while let Some(front) = self.samples.front() {
            if current_time.saturating_duration_since(front.timestamp) > self.window_duration {
                self.samples.pop_front();
            } else {
                break;
            }
        }
    }

    /// Computes moving average generation velocity in tokens per second.
    pub fn tokens_per_second(&self) -> f64 {
        self.tokens_per_second_at(Instant::now())
    }

    /// Computes moving average velocity at a specific instant.
    pub fn tokens_per_second_at(&self, current_time: Instant) -> f64 {
        if self.samples.is_empty() {
            return 0.0;
        }

        let earliest = self.samples.front().unwrap().timestamp;
        let elapsed = current_time.saturating_duration_since(earliest).as_secs_f64();

        let total_tokens: u64 = self.samples.iter().map(|s| s.tokens).sum();

        // If elapsed is effectively zero, avoid division by zero
        if elapsed <= 1e-4 {
            if total_tokens > 0 {
                total_tokens as f64
            } else {
                0.0
            }
        } else {
            total_tokens as f64 / elapsed
        }
    }

    /// Resets the tracker history.
    pub fn reset(&mut self) {
        self.samples.clear();
    }
}

impl Default for TokenVelocityTracker {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{Duration, Instant};

    #[test]
    fn test_context_saturation_percentages_and_thresholds() {
        // Normal (<80%)
        let normal = ContextSaturation::new(30_000, 10_000, 100_000);
        assert_eq!(normal.total_tokens, 40_000);
        assert!((normal.usage_pct - 40.0).abs() < 1e-3);
        assert_eq!(normal.threshold_warning(), SaturationThreshold::Normal);

        // Boundary 79.9% -> Normal
        let near_warning = ContextSaturation::new(799, 0, 1_000);
        assert!((near_warning.usage_pct - 79.9).abs() < 1e-3);
        assert_eq!(near_warning.threshold_warning(), SaturationThreshold::Normal);

        // Warning (80% ..= 90%)
        let warning_exact = ContextSaturation::new(80_000, 0, 100_000);
        assert!((warning_exact.usage_pct - 80.0).abs() < 1e-3);
        assert_eq!(warning_exact.threshold_warning(), SaturationThreshold::Warning);

        let warning_mid = ContextSaturation::new(60_000, 25_000, 100_000);
        assert_eq!(warning_mid.total_tokens, 85_000);
        assert!((warning_mid.usage_pct - 85.0).abs() < 1e-3);
        assert_eq!(warning_mid.threshold_warning(), SaturationThreshold::Warning);

        let warning_upper = ContextSaturation::new(90_000, 0, 100_000);
        assert!((warning_upper.usage_pct - 90.0).abs() < 1e-3);
        assert_eq!(warning_upper.threshold_warning(), SaturationThreshold::Warning);

        // Critical (>90%)
        let critical_boundary = ContextSaturation::new(901, 0, 1_000);
        assert!((critical_boundary.usage_pct - 90.1).abs() < 1e-3);
        assert_eq!(critical_boundary.threshold_warning(), SaturationThreshold::Critical);

        let critical = ContextSaturation::new(95_000, 3_000, 100_000);
        assert_eq!(critical.total_tokens, 98_000);
        assert!((critical.usage_pct - 98.0).abs() < 1e-3);
        assert_eq!(critical.threshold_warning(), SaturationThreshold::Critical);

        // Zero limit handling
        let zero_limit = ContextSaturation::new(100, 100, 0);
        assert_eq!(zero_limit.usage_pct, 0.0);
        assert_eq!(zero_limit.threshold_warning(), SaturationThreshold::Normal);
    }

    #[test]
    fn test_token_velocity_tracker() {
        let mut tracker = TokenVelocityTracker::with_window(Duration::from_secs(5));
        let start = Instant::now();

        // No samples -> 0 velocity
        assert_eq!(tracker.tokens_per_second_at(start), 0.0);

        // Emit 100 tokens at start
        tracker.record_tokens_at(100, start);
        // After 1 second, emit another 100 tokens
        let t1 = start + Duration::from_secs(1);
        tracker.record_tokens_at(100, t1);

        // Velocity over 1 second = (100 + 100) / 1.0 = 200 tok/sec
        let vel1 = tracker.tokens_per_second_at(t1);
        assert!((vel1 - 200.0).abs() < 1e-2);

        // After 2 seconds, emit another 100 tokens
        let t2 = start + Duration::from_secs(2);
        tracker.record_tokens_at(100, t2);

        // Velocity at t2 = (100 + 100 + 100) / 2.0 = 150 tok/sec
        let vel2 = tracker.tokens_per_second_at(t2);
        assert!((vel2 - 150.0).abs() < 1e-2);

        // Move beyond window (5 seconds), pruning oldest samples
        let t6 = start + Duration::from_secs(6);
        // at t6, sample from start (t=0) is 6s old > 5s window duration, should be pruned
        tracker.record_tokens_at(50, t6);

        // Remaining in window: t1 (100), t2 (100), t6 (50) -> total 250
        // Window elapsed since earliest active (t1 = 1s): 6 - 1 = 5s
        // Velocity = 250 / 5 = 50.0 tok/sec
        let vel6 = tracker.tokens_per_second_at(t6);
        assert!((vel6 - 50.0).abs() < 1e-2);
    }

    #[test]
    fn test_telemetry_config() {
        let default_cfg = TelemetryConfig::default();
        assert_eq!(default_cfg.sync_delay_secs, 5);
        assert!(!default_cfg.is_realtime());

        let realtime_cfg = TelemetryConfig::new(0);
        assert_eq!(realtime_cfg.sync_delay_secs, 0);
        assert!(realtime_cfg.is_realtime());

        let clamped_cfg = TelemetryConfig::new(25);
        assert_eq!(clamped_cfg.sync_delay_secs, 10);
    }

    #[test]
    fn test_room_context_breakdown() {
        let breakdown = RoomContextBreakdown::new(5_000, 20_000, 2_000, 3_000, 100_000);
        assert_eq!(breakdown.system_tokens, 5_000);
        assert_eq!(breakdown.history_tokens, 20_000);
        assert_eq!(breakdown.draft_tokens, 2_000);
        assert_eq!(breakdown.completion_tokens, 3_000);
        assert_eq!(breakdown.max_window, 100_000);
        assert_eq!(breakdown.free_tokens, 70_000);

        // Saturating overflow where used exceeds max_window
        let overflow = RoomContextBreakdown::new(50_000, 60_000, 0, 0, 100_000);
        assert_eq!(overflow.free_tokens, 0);
    }
}
