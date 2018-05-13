/****************************************************
 * 创建人：  @author caohui    
 * 创建时间: 2018/5/13/17:21
 * 项目名称: yhfin-risk-cloud-parent 
 * 文件名称: InputChannels.java
 * 文件描述: @Description 消息通道
 * 公司名称: 深圳市赢和信息技术有限公司
 *
 * All rights Reserved, Designed By 深圳市赢和信息技术有限公司
 * @Copyright:2016-2017
 *
 ********************************************************/
package com.yhfin.risk.cloud.calculate.channel;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.messaging.SubscribableChannel;

/**
 * 消息通道
 * 包名称：com.yhfin.risk.cloud.calculate.channel
 * 类名称：InputChannels
 * 类描述：消息通道
 * 创建人：@author caohui
 * 创建时间：2018/5/13/17:21
 */

public interface InputChannels {
    /**
     * 内存从风控同步
     *
     * @return
     */
    @Input("memory")
    SubscribableChannel memory();
}
