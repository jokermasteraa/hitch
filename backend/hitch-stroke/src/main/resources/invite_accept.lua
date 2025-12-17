---@diagnostic disable: undefined-global

-- KEYS[2]: 乘客行程的 Invite Hash Key
-- ARGV[1]: 乘客行程 ID (作为 field 存入司机 Hash)
-- ARGV[2]: 司机行程 ID (作为 field 存入乘客 Hash)
-- ARGV[3]: 最大座位数 (quantity)
-- ARGV[4]: 确认状态码 (例如 "1")

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
-- HGETALL 返回的是 {key1, val1, key2, val2...} 数组，所以步长为2，值在偶数位
for i = 2, #inviteMap, 2 do
    if inviteMap[i] == status then
        confirmedCount = confirmedCount + 1
    end
end

-- 3. 判断是否还有空位
if confirmedCount >= maxSeats then
    return 0 -- 失败，满员
end

-- 4. 执行更新操作 (原子性)
redis.call('HSET', driverKey, pTripId, status)
redis.call('HSET', passengerKey, dTripId, status)

return 1 -- 成功