package com.expenseflow.notification.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.notification.BaseController;
import com.expenseflow.notification.service.MessageService;
import com.expenseflow.notification.vo.MessageVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification/message")
@RequiredArgsConstructor
public class MessageController extends BaseController {

    private final MessageService messageService;

    @GetMapping("/page")
    public Result<Page<MessageVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return messageService.page(page, size, getCurrentUserId());
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        messageService.markAllRead(getCurrentUserId());
        return Result.ok();
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(messageService.unreadCount(getCurrentUserId()));
    }
}
