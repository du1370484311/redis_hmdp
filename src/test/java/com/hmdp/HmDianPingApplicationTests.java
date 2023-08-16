package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {
  @Resource
  ShopServiceImpl shopService;
  @Test
    void test(){
    shopService.saveToRedis(1L,30L);
  }
}
