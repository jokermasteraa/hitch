package com.heima.stroke.handler;

import com.heima.commons.constant.HtichConstants;
import com.heima.commons.domin.bo.GeoBO;
import com.heima.commons.domin.bo.HitchGeoBO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@RunWith(SpringRunner.class)
public class StrokeBenchmarkTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 模拟测试用的经纬度（北京附近）
    private final double CENTER_LNG = 116.40;
    private final double CENTER_LAT = 39.90;

    /**
     * 步骤1：造数据
     * 往 Redis 里塞入 1000 个乘客，都在我们测试点的附近
     */
    @Test
    public void seedData() {
        System.out.println("开始造数据...");
        // 使用 Pipeline 批量造数据，否则造数据都要很久
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (int i = 0; i < 1000; i++) {
                    String tripId = "TEST_TRIP_" + i;
                    // 在中心点附近随机生成坐标
                    double lng = CENTER_LNG + (Math.random() - 0.5) * 0.1;
                    double lat = CENTER_LAT + (Math.random() - 0.5) * 0.1;

                    // 写入乘客起点 (模拟已有行程)
                    connection.geoAdd(HtichConstants.STROKE_PASSENGER_GEO_START.getBytes(), new Point(lng, lat), tripId.getBytes());
                    // 写入乘客终点 (简单起见，终点也设在附近，确保能匹配上)
                    connection.geoAdd(HtichConstants.STROKE_PASSENGER_GEO_END.getBytes(), new Point(lng, lat), tripId.getBytes());
                }
                return null;
            }
        });
        System.out.println("造数据完成，Redis 中已有 1000 个附近的行程。");
    }

    @Test
    public void testSlowMatching() {
        // 1. 准备数据：假设我们已经匹配到了 500 个顺路的人 (模拟 geoFilterMatch 的结果)
        List<HitchGeoBO> mockMatchList = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            GeoBO geo = new GeoBO("TEST_TRIP_" + i, 10.0f, "116.4", "39.9");
            mockMatchList.add(new HitchGeoBO("TEST_TRIP_" + i, geo, geo));
        }

        String myTripId = "MY_CURRENT_TRIP_ID";

        // 2. 开始计时
        long start = System.currentTimeMillis();

        // 3. 模拟 initGeoData 中的循环逻辑 (这是你要优化的代码)
        for (HitchGeoBO hitchGeoBO : mockMatchList) {
            // 模拟 StrokeHandler.java 中的 4 次 Redis 调用
            // 这里的 redisTemplate 操作等同于 redisHelper 的调用

            // 操作1: 记录所有行程
            redisTemplate.opsForZSet().add(HtichConstants.STROKE_GEO_ZSET_PREFIX + hitchGeoBO.getTargetId(), myTripId, 90.0);
            // 操作2: 记录距离
            redisTemplate.opsForHash().put(HtichConstants.STROKE_GEO_DISTANCE_PREFIX + hitchGeoBO.getTargetId(), myTripId, "10:10");
            // 操作3: 我记录所有行程
            redisTemplate.opsForZSet().add(HtichConstants.STROKE_GEO_ZSET_PREFIX + myTripId, hitchGeoBO.getTargetId(), 90.0);
            // 操作4: 我记录距离
            redisTemplate.opsForHash().put(HtichConstants.STROKE_GEO_DISTANCE_PREFIX + myTripId, hitchGeoBO.getTargetId(), "10:10");
        }

        long end = System.currentTimeMillis();
        System.out.println("=========================================");
        System.out.println("【优化前】耗时: " + (end - start) + " ms");
        System.out.println("=========================================");
    }
    @Test
    public void testPipelineOptimization() {
        // 1. 准备同样的数据
        List<HitchGeoBO> mockMatchList = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            GeoBO geo = new GeoBO("TEST_TRIP_" + i, 10.0f, "116.4", "39.9");
            mockMatchList.add(new HitchGeoBO("TEST_TRIP_" + i, geo, geo));
        }

        String myTripId = "MY_CURRENT_TRIP_ID";

        // 2. 开始计时
        long start = System.currentTimeMillis();

        // 3. 使用 Pipeline 优化
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (HitchGeoBO hitchGeoBO : mockMatchList) {
                    byte[] targetIdBytes = hitchGeoBO.getTargetId().getBytes();
                    byte[] myIdBytes = myTripId.getBytes();
                    byte[] scoreBytes = String.valueOf(90.0).getBytes();
                    byte[] distanceBytes = "10:10".getBytes();

                    // 这里的 Key 转换逻辑要参考 RedisHelper
                    byte[] zsetKey1 = (HtichConstants.STROKE_GEO_ZSET_PREFIX + hitchGeoBO.getTargetId()).getBytes();
                    byte[] hashKey1 = (HtichConstants.STROKE_GEO_DISTANCE_PREFIX + hitchGeoBO.getTargetId()).getBytes();
                    byte[] zsetKey2 = (HtichConstants.STROKE_GEO_ZSET_PREFIX + myTripId).getBytes();
                    byte[] hashKey2 = (HtichConstants.STROKE_GEO_DISTANCE_PREFIX + myTripId).getBytes();

                    // 原生 Connection 操作，没有网络开销，只是塞入队列
                    connection.zAdd(zsetKey1, 90.0, myIdBytes);
                    connection.hSet(hashKey1, myIdBytes, distanceBytes);
                    connection.zAdd(zsetKey2, 90.0, targetIdBytes);
                    connection.hSet(hashKey2, targetIdBytes, distanceBytes);
                }
                return null;
            }
        });

        long end = System.currentTimeMillis();
        System.out.println("=========================================");
        System.out.println("【Pipeline优化后】耗时: " + (end - start) + " ms");
        System.out.println("=========================================");
    }

}