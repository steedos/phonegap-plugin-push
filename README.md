#phonegap-plugin-push [![Build Status](https://travis-ci.org/phonegap/phonegap-plugin-push.svg)](https://travis-ci.org/phonegap/phonegap-plugin-push)

> Register and receive push notifications

# What is this?

This plugin offers support to receive and handle native push notifications with a **single unified API**, and **with no dependency on any other plugins**.

- [Reporting Issues](docs/ISSUES.md)
- [Installation](docs/INSTALLATION.md)
- [API reference](docs/API.md)
- [Typescript support](docs/TYPESCRIPT.md)
- [Examples](docs/EXAMPLES.md)
- [Platform support](docs/PLATFORM_SUPPORT.md)
- [Cloud build support (PG Build, IntelXDK)](docs/PHONEGAP_BUILD.md)
- [Push notification payload details](docs/PAYLOAD.md)
- [Contributing](.github/CONTRIBUTING.md)
- [License (MIT)](MIT-LICENSE)


# 安装 
> cordova plugin add https://github.com/steedos/phonegap-plugin-push-aliyun.git --variable SENDER_ID=YOUR SENDER_ID

安装完成后，拷贝libs下的文件到 platforms/android的libs中

请在AndroidManifest.xml 文件中配置属性：
<meta-data
android:name="com.alibaba.app.appkey"
    android:value="******" /> <!--TODO  请填写应用对应的 appKye-->
<meta-data android:name="com.alibaba.app.appsecret" 
    android:value="******" /> <!--TODO  请填写应用对应的 appSecret-->