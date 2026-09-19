package cn.keking.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 分享密码验证页控制器
 * 渲染 share.ftl 密码页（页面自包含、无外部静态资源依赖），
 * 供 ShareAccessFilter 在"带密码且未验证"场景重定向跳转
 *
 * @author Claude Code
 */
@Controller
public class SharePageController {

    /**
     * 分享密码验证页，模板读取 ${shareToken}
     */
    @GetMapping("/share-page/{token}")
    public String sharePage(@PathVariable String token, Model model) {
        model.addAttribute("shareToken", token);
        return "/share";
    }
}
