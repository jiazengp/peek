package com.peek.manager.constants;

/**
 * 游戏相关的常量定义
 * 统一管理魔数，避免硬编码
 */
public class GameConstants {
    
    // === 时间相关常量 ===
    
    /** 秒转毫秒的乘数 */
    public static final long SECONDS_TO_MILLIS = 1000L;
    
    /** 秒转分钟的除数 */
    public static final long SECONDS_PER_MINUTE = 60L;
    
    /** 秒转小时的除数 */
    public static final long SECONDS_PER_HOUR = 3600L;
    
    /** 分钟转毫秒 */
    public static final long MINUTES_TO_MILLIS = 60L * SECONDS_TO_MILLIS;
    
    /** 小时转毫秒 */
    public static final long HOURS_TO_MILLIS = SECONDS_PER_HOUR * SECONDS_TO_MILLIS;
    
    // === Minecraft游戏限制常量 ===
    
    /** 玩家火焰刻度的最大合理值 */
    public static final int MAX_FIRE_TICKS = 32767;
    
    /** 玩家火焰刻度的最小值 */
    public static final int MIN_FIRE_TICKS = 0;
    
    /** 玩家空气值的最大值 */
    public static final int MAX_AIR_VALUE = 300;
    
    /** 玩家空气值的最小值 */
    public static final int MIN_AIR_VALUE = 0;
    
    /** 载具气泡时间的最小值 */
    public static final int MIN_VEHICLE_BUBBLE_TIME = 0;
    
    // === 清理和维护常量 ===
    
    /** 请求管理器清理间隔(ticks) - 30秒 */
    public static final int REQUEST_CLEANUP_INTERVAL_TICKS = 600;
    
    /** 会话管理器清理间隔(ticks) - 60秒 */
    public static final int SESSION_CLEANUP_INTERVAL_TICKS = 1200;
    
    /** 循环Peek记录过期时间(毫秒) - 1分钟 */
    public static final long CIRCULAR_PEEK_EXPIRY_MILLIS = 60L * SECONDS_TO_MILLIS;
    
    /** 长时间运行会话阈值(秒) - 1小时 */
    public static final long LONG_RUNNING_SESSION_THRESHOLD_SECONDS = SECONDS_PER_HOUR;
    
    // === 粒子效果常量 ===
    
    /** 默认粒子颜色(青色) */
    public static final int DEFAULT_PARTICLE_COLOR = 0x00FFFF;
    
    /** 十六进制颜色字符串的标准长度 */
    public static final int HEX_COLOR_LENGTH = 6;
    
    /** 最大合理的粒子数量(防止性能问题) */
    public static final int MAX_PARTICLES_PER_SPAWN = 10;
    
    /** 最小粒子数量 */
    public static final int MIN_PARTICLES_PER_SPAWN = 1;
    
    /** 最大合理的粒子扩散范围 */
    public static final double MAX_PARTICLE_SPREAD = 3.0;
    
    /** 最小粒子扩散范围 */
    public static final double MIN_PARTICLE_SPREAD = 0.1;
    
    /** 最大粒子速度 */
    public static final double MAX_PARTICLE_VELOCITY = 0.3;
    
    /** 最小粒子速度 */
    public static final double MIN_PARTICLE_VELOCITY = 0.0;
    
    // === 位置和距离常量 ===
    
    /** 粒子视线遮挡避免的最小X偏移 */
    public static final double PARTICLE_VIEW_OBSTRUCTION_X_THRESHOLD = 0.3;
    
    /** 粒子视线遮挡避免的最小Z偏移 */
    public static final double PARTICLE_VIEW_OBSTRUCTION_Z_THRESHOLD = -0.3;
    
    /** 粒子推离玩家视野的距离 */
    public static final double PARTICLE_PUSH_BACK_DISTANCE = 1.0;
    
    /** 粒子Y轴偏移的负偏差(向下偏移) */
    public static final double PARTICLE_Y_NEGATIVE_BIAS = 0.3;
    
    /** 粒子向上速度偏差 */
    public static final double PARTICLE_UPWARD_VELOCITY_BIAS = 0.5;

    private GameConstants() {
        throw new UnsupportedOperationException("Constants class should not be instantiated");
    }
}