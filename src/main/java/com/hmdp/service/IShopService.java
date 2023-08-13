package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 解决缓存穿透的问题
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 利用setnx原理 用互斥锁来实现缓存击穿的问题
     * @param id
     * @return
     */
    Result queryWithMutex(Long id);

    Result updateShop(Shop shop);
}
