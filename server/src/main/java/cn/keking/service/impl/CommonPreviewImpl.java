package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.utils.DownloadUtils;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Created by kl on 2018/1/17.
 * Content :图片文件处理
 */
@Component("commonPreview")
public class CommonPreviewImpl implements FilePreview {

    private final FileHandlerService fileHandlerService;
    private final OtherFilePreviewImpl otherFilePreview;

    public CommonPreviewImpl(FileHandlerService fileHandlerService, OtherFilePreviewImpl otherFilePreview) {
        this.fileHandlerService = fileHandlerService;
        this.otherFilePreview = otherFilePreview;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();
        String fileName = fileAttribute.getName();

        // 检查缓存
        if (!forceUpdatedCache && ConfigConstants.isCacheEnabled() && fileHandlerService.listConvertedFiles().containsKey(fileName)) {
            model.addAttribute("currentUrl", fileHandlerService.getConvertedFile(fileName));
            return null;
        }

        // 不是http开头，需下载到本地
        if (url != null && !url.toLowerCase().startsWith("http")) {
            ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
            if (response.isFailure()) {
                return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
            }
            String file = fileHandlerService.getRelativePath(response.getContent());
            model.addAttribute("currentUrl", file);
            if (ConfigConstants.isCacheEnabled()) {
                fileHandlerService.addConvertedFile(fileName, file);
            }
        } else {
            model.addAttribute("currentUrl", url);
        }
        return null;
    }
}