/****************************************************
 * 创建人：  @author caohui    
 * 创建时间: 2018/5/26/13:16
 * 项目名称: yhfin-risk-cloud-parent 
 * 文件名称: InputChannels.java
 * 文件描述: @Description 消息通道
 * 公司名称: 深圳市赢和信息技术有限公司
 *
 * All rights Reserved, Designed By 深圳市赢和信息技术有限公司
 * @Copyright:2016-2018
 *
 ********************************************************/
package com.yhfin.risk.cloud.analy.calculate.channel;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.messaging.SubscribableChannel;

/**
 * 消息通道
 * 包名称：com.yhfin.risk.cloud.analy.calculate.channel
 * 类名称：InputChannels
 * 类描述：消息通道
 * 创建人：@author caohui
 * 创建时间：2018/5/26/13:16
 */

public interface InputChannels {
    /**
     * 内存从风控同步
     *
     * @return
     */
    @Input("memory")
    SubscribableChannel memory();

    /**
     * 同步内存条目
     *
     * @return
     */
    @Input("entry")
    SubscribableChannel entry();
}
