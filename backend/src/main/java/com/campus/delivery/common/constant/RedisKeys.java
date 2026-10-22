package com.campus.delivery.common.constant;

/**
 * Redis Key 统一定义，禁止在各模块中散落硬编码字符串。
 *
 * <p>命名规范：{@code 业务:子业务:标识}，冒号分层，便于 {@code SCAN} 与运维排查。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 登录 Token 黑名单（退出登录时写入，值为过期时间戳） */
    public static final String TOKEN_BLACKLIST = "auth:token:blacklist:";

    /** 角色权限缓存：auth:perm:{role} */
    public static final String ROLE_PERMISSION = "auth:perm:";

    /** 热门店铺缓存（有序集合，score 为月销量） */
    public static final String HOT_SHOP = "cache:shop:hot";

    /** 热门菜品缓存（有序集合，score 为月销量） */
    public static final String HOT_DISH = "cache:dish:hot";

    /** 热门店铺榜单明细（List，与 HOT_SHOP 配套，省去回表查详情） */
    public static final String HOT_SHOP_LIST = "cache:shop:hot:list";

    /** 热门菜品榜单明细（List，与 HOT_DISH 配套，省去回表查详情） */
    public static final String HOT_DISH_LIST = "cache:dish:hot:list";

    /** 菜品详情缓存：cache:dish:detail:{dishId} */
    public static final String DISH_DETAIL = "cache:dish:detail:";

    /** 店铺详情缓存：cache:shop:detail:{shopId} */
    public static final String SHOP_DETAIL = "cache:shop:detail:";

    /** 骑手抢单池（有序集合，score 为出餐时间，保证先出餐先被看到） */
    public static final String GRAB_POOL = "delivery:grab:pool";

    /** 抢单分布式锁：lock:order:grab:{orderId} */
    public static final String LOCK_ORDER_GRAB = "lock:order:grab:";

    /**
     * 订单超时未接单延时队列（有序集合）。
     *
     * <p>member = orderId，score = 到期时间（epoch 毫秒）。
     * 下单时入队，商户接单/拒单/学生取消时出队；
     * 定时任务只扫描 score ≤ 当前时间 的成员，把仍然「待接单」的订单自动取消。
     *
     * <p>用 ZSet 而不是为每个订单起一个定时器：Redis 的 score 天生有序，
     * 一次 {@code ZRANGEBYSCORE 0 now} 就能拿到所有到期订单，O(log N)。
     */
    public static final String ORDER_TIMEOUT_QUEUE = "order:timeout:queue";

    /**
     * 菜品详情回源重建锁：lock:dish:cache:{dishId}
     *
     * <p>用于「缓存击穿」防护：热点 key 过期瞬间只允许一个线程回源查库并重建缓存。
     */
    public static final String LOCK_DISH_CACHE = "lock:dish:cache:";

    /** 下单扣库存分布式锁：lock:dish:stock:{dishId} */
    public static final String LOCK_DISH_STOCK = "lock:dish:stock:";

    /** 骑手在线状态：rider:online:{riderId}，值为最后心跳时间戳 */
    public static final String RIDER_ONLINE = "rider:online:";

    /** 骑手当前配送单数：rider:delivering:{riderId} */
    public static final String RIDER_DELIVERING_COUNT = "rider:delivering:";

    /** 客服对话上下文：ai:chat:context:{sessionId} */
    public static final String AI_CHAT_CONTEXT = "ai:chat:context:";

    /** 推荐结果缓存：ai:recommend:{userId}:{scene} */
    public static final String AI_RECOMMEND = "ai:recommend:";

    /**
     * 接口限流计数：rate:{name}:{subject}:{窗口序号}
     *
     * <p>窗口序号 = 当前秒 / 窗口长度，因此每个窗口是一个独立 key，
     * 天然过期、不会出现「key 永不过期导致用户被永久限流」的问题。
     */
    public static final String RATE_LIMIT = "rate:";

    /**
     * 下单幂等键：idempotent:order:{userId}:{requestId}
     *
     * <p>值为 PROCESSING（处理中）或订单ID（已成功）。
     * 重复提交时据此判断：返回原订单，或提示"请勿重复提交"。
     */
    public static final String IDEMPOTENT_ORDER = "idempotent:order:";
}
