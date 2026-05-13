package com.expenseflow.system.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.common.handler.ExpenseFlowTenantLineHandler;
import com.expenseflow.system.dto.UserDTO;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.service.UserService;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result<Page<UserVO>> page(int page, int size, String keyword) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        qw.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> pg = userMapper.selectPage(new Page<>(page, size), qw);
        Page<UserVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<UserVO> getById(Long id) {
        SysUser user = userMapper.selectById(id);
        return user == null ? Result.fail(404, "用户不存在") : Result.ok(toVO(user));
    }

    @Override
    @Transactional
    public Result<UserVO> create(UserDTO dto) {
        long count = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            return Result.fail(400, "用户名已存在");
        }

        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(passwordEncoder.encode(
            dto.getPassword() != null ? dto.getPassword() : "123456"));
        user.setStatus(1);
        Long tenantId = ExpenseFlowTenantLineHandler.getTenant();
        user.setTenantId(tenantId != null ? tenantId : 0L);
        userMapper.insert(user);
        return Result.ok(toVO(user));
    }

    @Override
    @Transactional
    public Result<UserVO> update(Long id, UserDTO dto) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }

        if (dto.getUsername() != null) {
            user.setUsername(dto.getUsername());
        }
        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userMapper.updateById(user);
        redisTemplate.delete("user:" + id);
        redisTemplate.delete("user:perm:" + id);
        return Result.ok(toVO(user));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        userMapper.deleteById(id);
        redisTemplate.delete("user:" + id);
        redisTemplate.delete("user:perm:" + id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> updateStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        redisTemplate.delete("user:" + id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> resetPassword(Long id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.ok();
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
