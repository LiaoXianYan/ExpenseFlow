package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.common.util.JwtUtil;
import com.expenseflow.system.entity.SysOauthUser;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.entity.SysUserRole;
import com.expenseflow.system.mapper.SysOauthUserMapper;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.mapper.SysUserRoleMapper;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/system/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final SysUserMapper userMapper;
    private final SysOauthUserMapper oauthUserMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/dingtalk/login")
    public Result<TokenVO> dingtalkLogin(@RequestBody(required = false) Map<String, String> body) {
        boolean mock = body != null && "true".equals(body.get("mock"));
        String mockOpenId = "mock_dingtalk_" + UUID.randomUUID().toString().substring(0, 8);
        String mockUnionId = "mock_union_" + UUID.randomUUID().toString().substring(0, 8);
        String openId = mock ? mockOpenId : (body != null ? body.get("openId") : null);
        if (openId == null) openId = mockOpenId;

        SysOauthUser oauth = oauthUserMapper.selectOne(
            new LambdaQueryWrapper<SysOauthUser>()
                .eq(SysOauthUser::getProvider, "dingtalk")
                .eq(SysOauthUser::getOpenId, openId));

        SysUser user;
        if (oauth != null) {
            user = userMapper.selectById(oauth.getUserId());
            if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
                return Result.fail(403, "用户已被禁用或不存在");
            }
        } else {
            // Auto-create user
            user = new SysUser();
            user.setTenantId(1L);
            user.setUsername("dingtalk_" + openId.substring(0, Math.min(12, openId.length())));
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRealName("钉钉用户" + openId.substring(0, Math.min(4, openId.length())));
            user.setStatus(1);
            userMapper.insert(user);

            SysOauthUser newOauth = new SysOauthUser();
            newOauth.setTenantId(1L);
            newOauth.setUserId(user.getId());
            newOauth.setProvider("dingtalk");
            newOauth.setOpenId(openId);
            newOauth.setUnionId(mockUnionId);
            oauthUserMapper.insert(newOauth);

            // Assign EMPLOYEE role (id=3)
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(3L);
            userRoleMapper.insert(ur);
        }

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getTenantId(), tokenId);
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getTenantId(), tokenId);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        return Result.ok(new TokenVO(accessToken, refreshToken, 7200, userVO));
    }
}
