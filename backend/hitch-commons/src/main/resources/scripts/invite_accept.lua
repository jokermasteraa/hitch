-- KEYS[1]: 司机行程 Hash Key
-- KEYS[2]: 乘客行程 Hash Key
-- ARGV[1]: 乘客行程 ID (作为 field)
-- ARGV[2]: 司机行程 ID (作为 field)
-- ARGV[3]: 最大座位数 (quantity)
-- ARGV[4]: 确认状态码 (通常是 1)

local driverKey = KEYS[1]
local passengerKey = KEYS[2]
local pTripId = ARGV[1]
local dTripId = ARGV[2]
local maxSeats = tonumber(ARGV[3])
local status = ARGV[4]

-- 1. 获取司机当前所有邀约状态
local inviteMap = redis.call('HGETALL', driverKey)
local confirmedCount = 0

-- 2. 遍历统计已确认("1")的人数
-- HGETALL 返回 {key, val, key, val...}，值在偶数位
for i = 2, #inviteMap, 2 do
    if inviteMap[i] == status then
        confirmedCount = confirmedCount + 1
    end
end

-- 3. 检查是否满员
if confirmedCount >= maxSeats then
    return 0 -- 失败：满员
end

-- 4. 原子写入状态：占座
redis.call('HSET', driverKey, pTripId, status)
redis.call('HSET', passengerKey, dTripId, status)

return 1 -- 成功