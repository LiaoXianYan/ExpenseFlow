package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.DictDataDTO;
import com.expenseflow.system.dto.DictTypeDTO;
import com.expenseflow.system.entity.SysDictData;
import com.expenseflow.system.entity.SysDictType;
import com.expenseflow.system.mapper.SysDictDataMapper;
import com.expenseflow.system.mapper.SysDictTypeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @GetMapping("/type/list")
    public Result<List<SysDictType>> typeList() {
        return Result.ok(dictTypeMapper.selectList(
            new LambdaQueryWrapper<SysDictType>().orderByDesc(SysDictType::getCreateTime)));
    }

    @PostMapping("/type")
    @AuditLog(module = "字典管理", operation = "CREATE_TYPE")
    public Result<SysDictType> createType(@Valid @RequestBody DictTypeDTO dto) {
        SysDictType t = new SysDictType();
        BeanUtils.copyProperties(dto, t);
        t.setStatus(1);
        dictTypeMapper.insert(t);
        return Result.ok(t);
    }

    @GetMapping("/data/list")
    public Result<List<SysDictData>> dataList(@RequestParam Long typeId) {
        return Result.ok(dictDataMapper.selectList(
            new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictTypeId, typeId)
                .orderByAsc(SysDictData::getSortOrder)));
    }

    @PostMapping("/data")
    @AuditLog(module = "字典管理", operation = "CREATE_DATA")
    public Result<SysDictData> createData(@Valid @RequestBody DictDataDTO dto) {
        SysDictData d = new SysDictData();
        BeanUtils.copyProperties(dto, d);
        d.setStatus(1);
        dictDataMapper.insert(d);
        return Result.ok(d);
    }

    @PutMapping("/data/{id}")
    @AuditLog(module = "字典管理", operation = "UPDATE_DATA")
    public Result<Void> updateData(@PathVariable Long id, @Valid @RequestBody DictDataDTO dto) {
        SysDictData d = dictDataMapper.selectById(id);
        if (d == null) return Result.fail(404, "字典数据不存在");
        BeanUtils.copyProperties(dto, d);
        d.setId(id);
        dictDataMapper.updateById(d);
        return Result.ok();
    }

    @DeleteMapping("/data/{id}")
    @AuditLog(module = "字典管理", operation = "DELETE_DATA")
    public Result<Void> deleteData(@PathVariable Long id) {
        dictDataMapper.deleteById(id);
        return Result.ok();
    }
}
