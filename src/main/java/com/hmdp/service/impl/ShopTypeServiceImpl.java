package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
   @Resource
   private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryAllShopType() {
        //1.先去redis中查询
        String key=SHOP_TYPE_KEY;
        String typeJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(typeJson)){
            List<ShopType> shopTypes = JSONUtil.toList(typeJson, ShopType.class);
            return shopTypes;
        }
        //如果为空 则表明redis中没有要去数据库查
        List<ShopType> shopTypes = getBaseMapper().selectList(new QueryWrapper<ShopType>().orderByDesc("sort"));
        //将其存入redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypes));
        return shopTypes;
    }
}
