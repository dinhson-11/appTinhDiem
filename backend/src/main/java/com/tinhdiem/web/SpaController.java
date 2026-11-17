package com.tinhdiem.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang bàn chơi do React định tuyến phía trình duyệt; server chỉ trả lại index.html. */
@Controller
public class SpaController {

    @GetMapping("/g/{id}")
    public String game() {
        return "forward:/index.html";
    }
}
