package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.TravelRequestDTO;
import com.expenseflow.expense.service.TravelRequestService;
import com.expenseflow.expense.vo.TravelRequestVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/travel")
@RequiredArgsConstructor
public class TravelRequestController extends BaseController {

    private final TravelRequestService travelService;

    @PreAuthorize("hasAuthority('travel:view')")
    @GetMapping("/page")
    public Result<Page<TravelRequestVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return travelService.page(page, size, getCurrentUserId(), status);
    }

    @PreAuthorize("hasAuthority('travel:view')")
    @GetMapping("/{id}")
    public Result<TravelRequestVO> getById(@PathVariable Long id) {
        return travelService.getById(id);
    }

    @PreAuthorize("hasAuthority('travel:create')")
    @PostMapping
    @AuditLog(module = "出差申请", operation = "CREATE")
    public Result<TravelRequestVO> create(@Valid @RequestBody TravelRequestDTO dto) {
        dto.setApplicantId(getCurrentUserId());
        return travelService.create(dto);
    }

    @PreAuthorize("hasAuthority('travel:edit')")
    @PutMapping("/{id}")
    @AuditLog(module = "出差申请", operation = "UPDATE")
    public Result<TravelRequestVO> update(@PathVariable Long id, @Valid @RequestBody TravelRequestDTO dto) {
        return travelService.update(id, dto);
    }

    @PreAuthorize("hasAuthority('travel:delete')")
    @DeleteMapping("/{id}")
    @AuditLog(module = "出差申请", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return travelService.delete(id);
    }

    @PostMapping("/{id}/submit")
    @AuditLog(module = "出差申请", operation = "SUBMIT")
    public Result<TravelRequestVO> submit(@PathVariable Long id) {
        return travelService.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @AuditLog(module = "出差申请", operation = "WITHDRAW")
    public Result<TravelRequestVO> withdraw(@PathVariable Long id) {
        return travelService.withdraw(id);
    }
}
