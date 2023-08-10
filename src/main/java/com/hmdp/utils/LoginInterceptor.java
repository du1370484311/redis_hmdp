package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 注意这里的拦截器不能注册到spring中
 */
public class LoginInterceptor implements HandlerInterceptor {
    private StringRedisTemplate  stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       //1.获取token 从请求头中获取token
        String token = request.getHeader("authorization");
        //根据token在redis中去获取用户
        String tokenKey=LOGIN_USER_KEY+token;

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        //这里比视频中要多一步 获取视频的类加载器  然后通过类加载器去创建对象
        //如果是 new UserDTO() 就不会多
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //3.判断用户是否存在
        if(user == null){
              //4.不存在，拦截，返回401状态码
              response.setStatus(401);
              return false;
        }
        //5.存在，保存用户信息到Threadlocal
        UserHolder.saveUser(user);
        //刷新token的过期时间
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //6.放行
        return true;
    }
}