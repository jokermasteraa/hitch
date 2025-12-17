package com.heima.commons.utils;

import com.alibaba.fastjson.JSON;
import com.heima.commons.constant.HtichConstants;
import com.heima.commons.domin.bo.GeoBO;
import com.heima.commons.domin.bo.ZsetResultBO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.*;

public class RedisHelper {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // --- 定义静态脚本对象，预加载脚本 ---
    private static DefaultRedisScript<Long> ACCEPT_SCRIPT;
    private static DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        // 加载抢座脚本
        ACCEPT_SCRIPT = new DefaultRedisScript<>();
        ACCEPT_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/invite_accept.lua")));
        ACCEPT_SCRIPT.setResultType(Long.class);

        // 加载回滚脚本
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/invite_rollback.lua")));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 原子操作：尝试占座
     */
    public boolean inviteAcceptAtomically(String prefix, String driverTripId, String passengerTripId, int maxSeats, String confirmedStatus) {
        List<String> keys = new ArrayList<>();
        keys.add(getRedisKey(prefix, driverTripId));    // KEYS[1]
        keys.add(getRedisKey(prefix, passengerTripId)); // KEYS[2]

        Long result = redisTemplate.execute(ACCEPT_SCRIPT, keys,
                passengerTripId,          // ARGV[1]
                driverTripId,             // ARGV[2]
                String.valueOf(maxSeats), // ARGV[3]
                confirmedStatus           // ARGV[4]
        );
        return result != null && result == 1;
    }

    /**
     * 补偿操作：回滚占座 (释放座位)
     */
    public void inviteRollback(String prefix, String driverTripId, String passengerTripId, String targetStatus) {
        List<String> keys = new ArrayList<>();
        keys.add(getRedisKey(prefix, driverTripId));
        keys.add(getRedisKey(prefix, passengerTripId));

        redisTemplate.execute(ROLLBACK_SCRIPT, keys,
                passengerTripId,
                driverTripId,
                targetStatus
        );
    }

    // ... 其他原有方法 (getRedisKey, addHash 等) ...
    private String getRedisKey(String prefix, String key) {
        return prefix + key;
    }

    public void setObject(String prefix, String key, Object value) {
        String redisKey = getRedisKey(prefix, key);
        String serializeData = JSON.toJSONString(value);
        redisTemplate.opsForValue().set(redisKey, serializeData);
    }

    public <T> T getObject(String prefix, String key, Class<T> clazz) {
        String redisKey = getRedisKey(prefix, key);
        String serializeData = redisTemplate.opsForValue().get(redisKey);
        return JSON.parseObject(serializeData, clazz);
    }

    public void delKey(String prefix, String key) {
        String redisKey = getRedisKey(prefix, key);
        redisTemplate.delete(redisKey);
    }

    public boolean exists(String prefix, String key) {
        String redisKey = getRedisKey(prefix, key);
        return redisTemplate.hasKey(redisKey);
    }

    public void addGEO(String prefix, String key, String lng, String lat, String tripId) {
        String redisKey = getRedisKey(prefix, key);
        Point point = new Point(Float.parseFloat(lng), Float.parseFloat(lat));
        redisTemplate.opsForGeo().add(redisKey, point, tripId);
    }

    public void delGEO(String prefix, String key, String tripId) {
        String redisKey = getRedisKey(prefix, key);
        redisTemplate.opsForGeo().remove(redisKey, tripId);
    }

    /**
     * 计算两点之间距离
     *
     * @param prefix
     * @param key
     * @param startLocation
     * @param endLocation
     * @return
     */
    public float geoDistance(String prefix, String key, String startLocation, String endLocation) {
        String redisKey = getRedisKey(prefix, key);
        Distance distance = redisTemplate.opsForGeo()
                .distance(redisKey, startLocation, endLocation, RedisGeoCommands.DistanceUnit.KILOMETERS);//params: key, 地方名称1, 地方名称2, 距离单位
        return (float) distance.getNormalizedValue();
    }


    public Map<String, GeoBO> geoNearByXY(String prefix, String key, float lng, float lat) {
        String redisKey = getRedisKey(prefix, key);
        //以当前坐标为中心画圆
        Circle circle = new Circle(
                new Point(lng, lat),
                new Distance(HtichConstants.STROKE_DIAMETER_RANGE, Metrics.KILOMETERS)
        );
        //限制20条，可以根据实际情况调整
        RedisGeoCommands.GeoRadiusCommandArgs args =
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates()
                .sortAscending().limit(20);
        //查找这个范围内的行程点
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .radius(redisKey, circle, args);
        return geoResultPack(results);
    }


    /**
     * 根据地址名词进行搜索
     *
     * @param prefix   前缀
     * @param key      key
     * @param location 地址:行程ID+role
     * @param isStart  是否时起始行程
     * @return
     */
    public Map<String, GeoBO> geoNearByPlace(String prefix, String key, String location, boolean isStart) {
        String redisKey = getRedisKey(prefix, key);
        Distance distance = new Distance(HtichConstants.STROKE_DIAMETER_RANGE, Metrics.KILOMETERS);//params: 距离量, 距离单位
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().includeCoordinates().sortAscending().limit(20);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .radius(redisKey, location, distance, args);//params: key, 地方名称, Circle, GeoRadiusCommandArgs
        return geoResultPack(results);

    }

    public void addHash(String prefix, String key, String hkey, String value) {
        String redisKey = getRedisKey(prefix, key);
        redisTemplate.opsForHash().put(redisKey, hkey, value);
    }

    public void delHash(String prefix, String key, String... hkeys) {
        String redisKey = getRedisKey(prefix, key);
        redisTemplate.opsForHash().delete(redisKey, hkeys);
    }

    public String getHash(String prefix, String key, String hkey) {
        String redisKey = getRedisKey(prefix, key);
        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
        return hashOperations.get(redisKey, hkey);
    }


    public Map<String, String> getHashByMap(String prefix, String key) {
        String redisKey = getRedisKey(prefix, key);
        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
        return hashOperations.entries(redisKey);
    }

    public void addZset(String prefix, String key, String value, float score) {
        String redisKey = getRedisKey(prefix, key);
        redisTemplate.opsForZSet().add(redisKey, value, score);
    }

    public List<ZsetResultBO> getZsetSortVaues(String prefix, String key) {
        String redisKey = getRedisKey(prefix, key);
        List<ZsetResultBO> zsetResultBOList = new ArrayList<>();
        //rangeByScoreWithScores(K,Smin,Smax,[offset],[count])
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(redisKey, 0, 100,0,20);
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            zsetResultBOList.add(new ZsetResultBO(typedTuple.getScore().floatValue(), typedTuple.getValue()));
        }
        return zsetResultBOList;
    }

    public float getZsetScore(String prefix, String key, String value) {
        String redisKey = getRedisKey(prefix, key);
        return redisTemplate.opsForZSet().score(redisKey, value).floatValue();
    }

    /**
     * 删除zset数据
     *
     * @param prefix
     * @param key
     * @param value
     */
    public void delZsetByKey(String prefix, String key, String value) {
        String redisKey = getRedisKey(prefix, key);
        redisTemplate.opsForZSet().remove(redisKey, value);
    }


    /**
     * GEO结果集包装
     *
     * @param results
     * @return
     */
    private Map<String, GeoBO> geoResultPack(GeoResults<RedisGeoCommands.GeoLocation<String>> results) {
        Map<String, GeoBO> geoBOMap = new HashMap();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            RedisGeoCommands.GeoLocation<String> content = result.getContent();
            String name = content.getName();
            // 距离中心点的距离
            Distance dis = result.getDistance();
            Point pos = content.getPoint();
            geoBOMap.put(name, new GeoBO(name, ((float) dis.getValue()), String.valueOf(pos.getX()), String.valueOf(pos.getY())));
        }
        return geoBOMap;
    }

}
