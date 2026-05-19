package com.iwatchme.locksim

/**
 * 线程栈帧里开的"锁记录"。两种用途靠 [displacedHeader] 区分:
 *
 *  1) **轻量级锁的真 LR** (displacedHeader != null)
 *     里面存的是 obj.header 被替换前的原始值,解锁时要 CAS 把这值写回 Mark Word。
 *     每个 obj 的栈里只有"最底下那条"是真 LR,负责恢复 Mark Word。
 *
 *  2) **sentinel / 哨兵 LR** (displacedHeader == null)
 *     sentinel 这个词在计算机里到处都是 —— C 字符串末尾的 \0、链表的哨兵节点、
 *     数据库的墓碑标记…… 共同点是 "只是个占位符,不携带真实数据"。
 *     这里 sentinel LR 只为占一格,表示"线程当前还在这个 obj 的临界区里",
 *     给 isInSyncBlock() 扫栈用。出栈时直接 pop,不动 Mark Word、不做 CAS。
 *
 *     什么时候压 sentinel:
 *       - 偏向锁首次进入 (HotSpot 原版不压,模拟器为了让 revoke 能扫到深度才压)
 *       - 偏向锁重入
 *       - 轻量级锁重入 (HotSpot 原版也压)
 *
 * 设计精髓: 同一个 obj 一摞 LR,只有栈底那条做"恢复 Mark Word"这件事,
 * 上面的 sentinel 全是重入计数,exit 时一路 pop,只有最外层那次才动对象头。
 */
class LockRecord(
    val owner: SimObject,
    var displacedHeader: Long?,
)
