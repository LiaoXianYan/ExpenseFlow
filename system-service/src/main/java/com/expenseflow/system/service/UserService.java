package com.expenseflow.system.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.UserDTO;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface UserService {
    Result<Page<UserVO>> page(int page, int size, String keyword);

    Result<UserVO> getById(Long id);

    Result<UserVO> create(UserDTO dto);

    Result<UserVO> update(Long id, UserDTO dto);

    Result<Void> delete(Long id);

    Result<Void> updateStatus(Long id, Integer status);

    Result<Void> resetPassword(Long id, String newPassword);
}
