README.txt
log目录用于存放kkFileView的运行日志。当前默认会输出：
1. kkFileView.log：主业务日志
2. kkFileView-error.log：错误日志
日志目录默认跟随应用配置中的 kk.log.dir / 环境变量 KK_LOG_DIR 或 LOG_PATH；在发布脚本中默认指向 ../log。
这些日志可以提供给开发、运维排查系统问题。如果通过kkFileView.log还无法定位问题所在，请你在寻求
kk官方支持时，QQ群一 613025121、QQ群二 484680571。将此日志文件一并携带，并按照这个
格式重命名日志文件 kkFileView-QQ昵称-时间日期.log。如：kkFileView-kl博主-2020-12-27.log 。
所有收集的日志文件我们都会存档，供所有的kk用户作为排查案例使用，所以在你提供日志前，请
先自行处理日志文件里的业务敏感内容。
