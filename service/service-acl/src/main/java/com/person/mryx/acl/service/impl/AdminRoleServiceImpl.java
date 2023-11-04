package com.person.mryx.acl.service.impl;

import com.person.mryx.acl.mapper.AdminRoleMapper;
import com.person.mryx.acl.service.AdminRoleService;
import com.person.mryx.model.acl.AdminRole;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, AdminRole> implements AdminRoleService {
}
