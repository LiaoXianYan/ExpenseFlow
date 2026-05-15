package com.expenseflow.notification.service;

import com.expenseflow.notification.dto.TemplateDTO;
import com.expenseflow.notification.vo.TemplateVO;

import java.util.List;

public interface TemplateService {
    List<TemplateVO> list();
    TemplateVO getByCode(String templateCode);
    TemplateVO create(TemplateDTO dto);
    TemplateVO update(Long id, TemplateDTO dto);
}
