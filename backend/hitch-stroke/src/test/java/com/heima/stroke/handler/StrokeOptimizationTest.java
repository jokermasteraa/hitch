package com.heima.stroke.handler;

import com.heima.commons.constant.HtichConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@RunWith(SpringRunner.class)
public class StrokeOptimizationTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 模拟匹配到的 20 个用户 ID
    private List<String> matchTripIds = new ArrayList<>();
    // 我的行程 ID
    private final String MY_TRIP_ID = "MY_TRIP_001";

    // 初始化测试数据
    {
        for (int i = 0; i < 20; i++) {
            matchTripIds.add("MATCH_USER_" + i);
        }
    }

    /**
     * 测试 1：普通循环写入 (模拟优化前)
     * 场景：匹配 20 人，产生 80 次网络 IO
     */
    @Test
    public void testSlowWrite() {
        System.out.println("====== 开始测试：普通循环写入 (20条数据) ======");

        // 1. 清理旧数据，防止干扰
        cleanData();

        long start = System.currentTimeMillis();

        // 2. 模拟业务逻辑：双向写入
        for (String targetTripId : matchTripIds) {
            // -------------------------------------------------
            // 每一行代码都是一次独立的网络请求 (Ping-Pong)
            // -------------------------------------------------

            // 动作1：在对方的列表里，记下我 (ZSet + Hash)
            String targetZsetKey = HtichConstants.STROKE_GEO_ZSET_PREFIX + targetTripId;
            String targetHashKey = HtichConstants.STROKE_GEO_DISTANCE_PREFIX + targetTripId;

            redisTemplate.opsForZSet().add(targetZsetKey, MY_TRIP_ID, 95.5); // 网络 IO #1
            redisTemplate.opsForHash().put(targetHashKey, MY_TRIP_ID, "5.2km:4.1km"); // 网络 IO #2

            // 动作2：在我的列表里，记下对方 (ZSet + Hash)
            String myZsetKey = HtichConstants.STROKE_GEO_ZSET_PREFIX + MY_TRIP_ID;
            String myHashKey = HtichConstants.STROKE_GEO_DISTANCE_PREFIX + MY_TRIP_ID;

            redisTemplate.opsForZSet().add(myZsetKey, targetTripId, 95.5); // 网络 IO #3
            redisTemplate.opsForHash().put(myHashKey, targetTripId, "5.2km:4.1km"); // 网络 IO #4
        }

        long end = System.currentTimeMillis();
        System.out.println("【优化前】耗时: " + (end - start) + " ms");
        System.out.println("============================================");
    }

    /**
     * 测试 2：Pipeline 管道写入 (模拟优化后)
     * 场景：匹配 20 人，合并为 1 次网络 IO
     */
    @Test
    public void testPipelineWrite() {
        System.out.println("====== 开始测试：Pipeline 管道写入 (20条数据) ======");

        // 1. 清理旧数据
        cleanData();

        long start = System.currentTimeMillis();

        // 2. 使用 Pipeline 打包命令
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (String targetTripId : matchTripIds) {
                    // 准备 Key 和 Value 的字节数组 (底层连接只认 byte[])
                    byte[] targetZsetKey = (HtichConstants.STROKE_GEO_ZSET_PREFIX + targetTripId).getBytes();
                    byte[] targetHashKey = (HtichConstants.STROKE_GEO_DISTANCE_PREFIX + targetTripId).getBytes();
                    byte[] myZsetKey = (HtichConstants.STROKE_GEO_ZSET_PREFIX + MY_TRIP_ID).getBytes();
                    byte[] myHashKey = (HtichConstants.STROKE_GEO_DISTANCE_PREFIX + MY_TRIP_ID).getBytes();

                    byte[] myIdBytes = MY_TRIP_ID.getBytes();
                    byte[] targetIdBytes = targetTripId.getBytes();
                    byte[] distanceBytes = "5.2km:4.1km".getBytes();

                    // -------------------------------------------------
                    // 这里的所有操作都只是“塞进信封”，不发网络请求
                    // -------------------------------------------------

                    // 动作1：对方记录我
                    connection.zAdd(targetZsetKey, 95.5, myIdBytes);
                    connection.hSet(targetHashKey, myIdBytes, distanceBytes);

                    // 动作2：我记录对方
                    connection.zAdd(myZsetKey, 95.5, targetIdBytes);
                    connection.hSet(myHashKey, targetIdBytes, distanceBytes);
                }
                return null;
            }
        });

        long end = System.currentTimeMillis();
        System.out.println("【优化后】耗时: " + (end - start) + " ms");
        System.out.println("============================================");
    }

    // 辅助方法：清理数据
    private void cleanData() {
        redisTemplate.delete(HtichConstants.STROKE_GEO_ZSET_PREFIX + MY_TRIP_ID);
        redisTemplate.delete(HtichConstants.STROKE_GEO_DISTANCE_PREFIX + MY_TRIP_ID);
        for (String id : matchTripIds) {
            redisTemplate.delete(HtichConstants.STROKE_GEO_ZSET_PREFIX + id);
            redisTemplate.delete(HtichConstants.STROKE_GEO_DISTANCE_PREFIX + id);
        }
    }
}