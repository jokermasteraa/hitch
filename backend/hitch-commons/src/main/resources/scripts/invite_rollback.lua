-- KEYS[1]: 司机行程 Hash Key
-- KEYS[2]: 乘客行程 Hash Key
-- ARGV[1]: 乘客行程 ID
-- ARGV[2]: 司机行程 ID
-- ARGV[3]: 回滚后的状态 (通常是 0: UNCONFIRMED)

-- 直接覆盖状态，将其改回未确认，从而释放座位
redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
redis.call('HSET', KEYS[2], ARGV[2], ARGV[3])
return 1