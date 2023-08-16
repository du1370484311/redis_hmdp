package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.先去redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否为空
        if (StrUtil.isNotBlank(shopJson)) {
            //不为空则直接返回
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        //增加一个判断  如果是空则直接返回空数据
        if (shopJson == null || shopJson.equals("")) {
            return Result.ok(null);
        }
        //如果为空 说明redis没有 要查数据库
        Shop shop = getById(id);
        if (shop == null) {
            //解决缓存穿透的问题  即数据为空也将空对象存入缓存中
            //设置过期时间为两分钟
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        //找到商铺 则先存入redis中  设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        //设置过期时间
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
    }

    @Override
    //3.保证原子性
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不存在");
        }
        //1.先操作数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Shop queryWithLogicExpire(Long id) {
        String key=CACHE_SHOP_KEY+id;
        //1.先去缓存中查询一下是否有数据
        //1.1没有数据直接返回空
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)){
            return null;
        }
        //1.2有数据 判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //这里要多转一步
        JSONObject data = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //1.3没有过期  则直接返回数据
        if (expireTime.isAfter(LocalDateTime.now())){
           return shop;
        }
        //1.4 过期了  尝试去获取锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock = getLock(lockKey);
        //获取成功 则开启独立线程去查询数据库 然后重建缓存
        if (isLock){
            //这里要做double check
            //为了避免一个线程释放锁的同时另一个线程获取到了锁，那么之后的缓存重建就没有必要了
            //过期了
            if (!expireTime.isAfter(LocalDateTime.now())){
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try {
                        this.saveToRedis(id,20L);
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
                    finally {
                        unlock(lockKey);
                    }
                });
            }

        }
        return shop;
    }

    @Override
    public Result queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.先去redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否为空
        if (StrUtil.isNotBlank(shopJson)) {
            //不为空则直接返回
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        //增加一个判断  如果是空则直接返回空数据
        //不等于null 那就一定是空字符串
        if (shopJson !=null ) {
            return Result.ok(null);
        }
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            //如果为空 说明redis没有 要先获取锁
            boolean lock = getLock(lockKey);
            //如果获取到锁 则查询数据库再操作缓存
            if (lock) {
                shop = getById(id);
                if (shop ==null){
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    //如果为空则不需要释放锁  不能让他往下走
                    return Result.ok(null);
                }
                else{
                    //找到商铺 则先存入redis中  设置过期时间
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
                    //设置过期时间
                    stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                    Thread.sleep(200);
                }
            } else {
                Thread.sleep(50);
                //如果没有拿到锁也不能让他释放锁
                return queryWithMutex(id);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return Result.ok(shop);

    }

    /**
     * 得到锁  释放锁则直接删除key即可
     *
     * @param lockKey
     * @return
     */
    private boolean getLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        //利用工具类拆箱
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


    /**
     * 将数据存入redis中
     * @param id  店铺id
     * @param expireSeconds  过期的秒数
     */
    public void saveToRedis(Long id,Long expireSeconds){
        Shop shop = getById(id);
        //将数据装入redisData中
        RedisData data = new RedisData();
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        data.setData(shop);
        //data对象转成json存储
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(data));
    }

}
