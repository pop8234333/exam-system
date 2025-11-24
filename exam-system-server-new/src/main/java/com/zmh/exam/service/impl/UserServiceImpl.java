package com.zmh.exam.service.impl;

import com.zmh.exam.entity.User;
import com.zmh.exam.mapper.UserMapper;
import com.zmh.exam.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.stereotype.Service;

/**
 * 用户Service实现类
 * 实现用户相关的业务逻辑
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

} 