package com.expenseflow.notification.service.impl;

import com.expenseflow.notification.dto.TemplateDTO;
import com.expenseflow.notification.entity.NtNotificationTemplate;
import com.expenseflow.notification.mapper.NtNotificationTemplateMapper;
import com.expenseflow.notification.service.TemplateService;
import com.expenseflow.notification.vo.TemplateVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final NtNotificationTemplateMapper templateMapper;

    @Override
    public List<TemplateVO> list() {
        return templateMapper.selectList(
            new LambdaQueryWrapper<NtNotificationTemplate>()
                .eq(NtNotificationTemplate::getStatus, 1)
                .orderByDesc(NtNotificationTemplate::getCreateTime)
        ).stream().map(t -> {
            TemplateVO vo = new TemplateVO();
            BeanUtils.copyProperties(t, vo);
            return vo;
        }).toList();
    }

    @Override
    public TemplateVO getByCode(String templateCode) {
        NtNotificationTemplate t = templateMapper.selectOne(
            new LambdaQueryWrapper<NtNotificationTemplate>()
                .eq(NtNotificationTemplate::getTemplateCode, templateCode));
        if (t == null) return null;
        TemplateVO vo = new TemplateVO();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }

    @Override
    @Transactional
    public TemplateVO create(TemplateDTO dto) {
        NtNotificationTemplate t = new NtNotificationTemplate();
        BeanUtils.copyProperties(dto, t);
        t.setStatus(1);
        templateMapper.insert(t);
        TemplateVO vo = new TemplateVO();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }

    @Override
    @Transactional
    public TemplateVO update(Long id, TemplateDTO dto) {
        NtNotificationTemplate t = templateMapper.selectById(id);
        if (t == null) return null;
        BeanUtils.copyProperties(dto, t);
        t.setId(id);
        templateMapper.updateById(t);
        TemplateVO vo = new TemplateVO();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }
}
