package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key=CACHE_SHOP_KEY + id;
        //1.先去redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否为空
        if (!StringUtil.isNullOrEmpty(shopJson)) {
            //不为空则直接返回
            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
        }
        //如果为空 说明redis没有 要查数据库
        Shop shop = getById(id);
        if (shop==null){
            return Result.fail("该商铺不存在");
        }
        //找到商铺 则先存入redis中  设置过期时间
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        //设置过期时间
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
    }

    @Override
    //3.保证原子性
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id ==null){
            return Result.fail("店铺id不存在");
        }
        //1.先操作数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+ id);
        return  Result.ok();
    }
}
