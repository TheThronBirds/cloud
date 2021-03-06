/****************************************************
 * 创建人：  @author caohui    
 * 创建时间: 2018年5月21日/上午10:47:58
 * 项目名称: yhfin-risk-cloud-notice 
 * 文件名称: OverallManagerServiceImpl.java
 * 文件描述: @Description 把通知中心接收 的消息放入队列中，按照顺序一个一个进行处理；通过轮询方式发送同步内存 同步条目消息
 * 公司名称: 深圳市赢和信息技术有限公司
 *
 * All rights Reserved, Designed By 深圳市赢和信息技术有限公司
 * @Copyright:2016-2018
 *
 ********************************************************/
package com.yhfin.risk.cloud.notice.service.local.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.yhfin.risk.cloud.notice.service.local.IOverallManagerService;
import com.yhfin.risk.cloud.notice.service.local.IStaticCalculateManageService;
import com.yhfin.risk.core.common.pojos.dtos.notice.StaticCalculateDTO;
import com.yhfin.risk.core.common.pojos.dtos.synchronizate.EntryMessageSynchronizateDTO;
import com.yhfin.risk.core.common.pojos.dtos.synchronizate.MemoryMessageSynchronizateDTO;
import com.yhfin.risk.core.common.reponse.ServerResponse;
import com.yhfin.risk.core.common.utils.StringUtil;
import com.yhfin.risk.core.sql.build.IEntryBuildSqlService;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 把通知中心接收 的消息放入队列中，按照顺序一个一个进行处理；通过轮询方式发送同步内存 同步条目消息
 * 包名称：com.yhfin.risk.cloud.notice.service.local.impl
 * 类名称：OverallManagerServiceImpl 类描述：通过轮询方式发送同步内存 同步条目消息 创建人：@author caohui
 * 创建时间：2018/5/13/12:20
 */
@Slf4j
@Service
public class OverallManagerServiceImpl implements IOverallManagerService {

    @Autowired
    private DiscoveryClient client;

    @Autowired
    private IStaticCalculateManageService calculateManageService;

    @Autowired
    private RestTemplate restTemplate;

    private ExecutorService handerMassegePool = new ThreadPoolExecutor(1, 1, 1000L, TimeUnit.MICROSECONDS,
            new LinkedBlockingQueue<Runnable>(20000), (runnable) -> {
        return new Thread(runnable, "取出静态基金分析计算、同步条目、同步内存信息线程池单线程");
    });

    @Autowired
    private IEntryBuildSqlService buildSqlService;

    private BlockingDeque<Message<?>> messageDeque;

    {
        this.messageDeque = new LinkedBlockingDeque<>(10000);
    }

    @PostConstruct
    /**
     * 类初始启动线程从队列中获取消息，进行处理
     *
     * @Title init
     * @Description: 类初始启动线程从队列中获取消息，进行处理
     * @author: caohui
     * @Date: 2018年5月21日/上午11:02:19
     */
    private void init() {
        handerMassegePool.execute(() -> {
            while (true) {
                try {
                    Object message = messageDeque.take().getMessage();
                    if (message != null) {
                        if (message instanceof EntryMessageSynchronizateDTO) {
                            realHanderEntrySynchronizateMessage((EntryMessageSynchronizateDTO) message);
                        } else if (message instanceof MemoryMessageSynchronizateDTO) {
                            realHanderMemorySynchronizateMessage((MemoryMessageSynchronizateDTO) message);
                        } else if (message instanceof StaticCalculateDTO) {
                            realHanderStaticCalculateRequest((StaticCalculateDTO) message);
                        }
                    }
                } catch (InterruptedException e) {
                    if (log.isErrorEnabled()) {
                        log.error("取出静态基金分析计算、同步条目、同步内存信息失败", e);
                    }
                }
            }

        });
    }

    /**
     * 接收前台的消息包装类 包名称：com.yhfin.risk.cloud.notice.service.local.impl 类名称：Message
     * 类描述：接收前台的消息包装类 创建人：@author caohui 创建时间：2018/5/13/12:20
     */
    @Setter
    @Getter
    @RequiredArgsConstructor
    private class Message<T> {
        @NonNull
        private T message;
    }

    /**
     * 真正处理同步条目消息 通过轮询方式发送同步条目消息
     *
     * @Title realHanderEntrySynchronizateMessage
     * @Description: 真正处理同步条目消息 通过轮询方式发送同步条目消息
     * @author: caohui
     * @Date: 2018年5月21日/上午11:16:48
     */
    private void realHanderEntrySynchronizateMessage(EntryMessageSynchronizateDTO message) {
        try {
            if (log.isInfoEnabled()) {
                log.info(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId()) + ",开始处理同步条目消息");
            }
            while (calculateManageService.getCalculateProcess()) {
                Thread.sleep(100);
            }
            if (message.getBuildSql() != null && message.getBuildSql()) {
                if (message.getSynchronizateAll() != null && message.getSynchronizateAll()) {
                    buildSqlService.entryBuildSqlAll();
                } else {
                    List<String> updateRiskIds = message.getUpdateRiskIds();
                    if (updateRiskIds != null && !updateRiskIds.isEmpty()) {
                        buildSqlService.entryBuildSqls(updateRiskIds.toArray(new String[updateRiskIds.size()]));
                    }
                }
            }
            List<ServiceInstance> instances = client.getInstances("RISK-ANALY-CALCULATE");
            if (instances != null && !instances.isEmpty()) {
                for (ServiceInstance instance : instances) {
                    String host = instance.getHost();
                    int port = instance.getPort();
                    if (log.isInfoEnabled()) {
                        log.info(
                                StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                                        + ",开始轮询发送同步条目消息,发送地址:{}",
                                "http://" + host + ":" + port + "/yhfin/cloud/analyCalculate/entrySynchronizate");
                    }
                    restTemplate.postForObject(
                            "http://" + host + ":" + port + "/yhfin/cloud/analyCalculate/entrySynchronizate",
                            message, ServerResponse.class);
                }
            }


        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId()) + ",同步条目消息处理失败",
                        e);
            }
        }

    }

    /**
     * 真正处理同步内存同步消息 通过轮询方式发送给同步内存消息
     *
     * @Title realHanderMemorySynchronizateMessage
     * @Description: 真正处理同步内存同步消息 通过轮询方式发送给同步内存消息
     * @author: caohui
     * @Date: 2018年5月21日/上午11:16:52
     */
    private void realHanderMemorySynchronizateMessage(MemoryMessageSynchronizateDTO message) {
        try {
            if (log.isInfoEnabled()) {
                log.info(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId()) + ",开始处理同步内存消息");
            }
            while (calculateManageService.getCalculateProcess()) {
                Thread.sleep(100);
            }
            List<ServiceInstance> analyInstances = client.getInstances("RISK-ANALY-CALCULATE");
            if (analyInstances == null) {
                analyInstances = new ArrayList<>(10);
            }
            if (analyInstances != null && !analyInstances.isEmpty()) {
                for (ServiceInstance instance : analyInstances) {
                    String host = instance.getHost();
                    int port = instance.getPort();
                    if (log.isInfoEnabled()) {
                        log.info(
                                StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                                        + ",开始轮询发送同步内存消息,发送地址:{}",
                                "http://" + host + ":" + port + "/yhfin/cloud/analyCalculate/memorySynchronizate");
                    }
                    restTemplate.postForObject(
                            "http://" + host + ":" + port + "/yhfin/cloud/analyCalculate/memorySynchronizate",
                            message, ServerResponse.class);
                }
            }

        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId()) + ",同步内存消息处理失败",
                        e);
            }
        }

    }

    /**
     * 真正处理同步内存同步消息 通过轮询方式发送给同步内存消息
     *
     * @Title realHanderMemorySynchronizateMessage
     * @Description: 真正处理同步内存同步消息 通过轮询方式发送给同步内存消息
     * @author: caohui
     * @Date: 2018年5月21日/上午11:16:52
     */
    @Override
    public List<Object> handerMemorySynchronizateStatusMessage() {
        List<Object> synchronizateTableDataStatusDTOs = null;
        try {
            if (log.isInfoEnabled()) {
                log.info("开始处理内存同步状态查询消息");
            }

            while (calculateManageService.getCalculateProcess()) {
                Thread.sleep(100);
            }
            List<ServiceInstance> analyInstances = client.getInstances("RISK-ANALY-CALCULATE");
            if (analyInstances == null) {
                analyInstances = new ArrayList<>(10);
            }
            if (analyInstances != null && !analyInstances.isEmpty()) {
                synchronizateTableDataStatusDTOs = new ArrayList<>();
                for (ServiceInstance instance : analyInstances) {
                    String host = instance.getHost();
                    int port = instance.getPort();
                    if (log.isInfoEnabled()) {
                        log.info("开始轮询发送内存同步状态查询消息,发送地址:{}", "http://" + host + ":" + port
                                + "/yhfin/cloud/analyCalculate/memorySynchronizateStatus");
                    }
                    ServerResponse serviceResponse = restTemplate.postForObject(
                            "http://" + host + ":" + port + "/yhfin/cloud/analyCalculate/memorySynchronizateStatus",
                            "", ServerResponse.class);
                    synchronizateTableDataStatusDTOs.add(serviceResponse.getData());
//						log.info("host{}", instance.getHost());
//						log.info("serviceId{}", instance.getServiceId());
//						log.info("uri{}", instance.getUri());
                }
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("内存同步状态查询失败", e);
            }
        }
        return synchronizateTableDataStatusDTOs;
    }

    @Override
    public void handerEntrySynchronizateMessage(EntryMessageSynchronizateDTO message) {
        if (log.isInfoEnabled()) {
            log.info(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                    + ",接收到同步条目消息,把消息放入队列中,等待处理");
        }
        try {
            messageDeque.put(new Message<EntryMessageSynchronizateDTO>(message));
        } catch (InterruptedException e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                        + ",接收到同步条目消息,把消息放入队列中出现错误");
                log.error("错误原因:", e);
            }
        }
    }

    @Override
    public void handerMemorySynchronizateMessage(MemoryMessageSynchronizateDTO message) {
        if (log.isInfoEnabled()) {
            log.info(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                    + ",接收到同步内存消息,把消息放入队列中,等待处理");
        }
        try {
            messageDeque.put(new Message<MemoryMessageSynchronizateDTO>(message));
        } catch (InterruptedException e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                        + ",接收到同步内存消息,把消息放入队列中出现错误");
                log.error("错误原因:", e);
            }
        }

    }

    @Override
    public void handerStaticCalculateRequest(StaticCalculateDTO staticCalculate) {
        if (log.isInfoEnabled()) {
            log.info(StringUtil.commonLogStart(staticCalculate.getSerialNumber(), staticCalculate.getRequestId())
                    + ",把静态计算请求放入队列中,等待后处理");
        }

        try {
            messageDeque.put(new Message<StaticCalculateDTO>(staticCalculate));
        } catch (InterruptedException e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(staticCalculate.getSerialNumber(), staticCalculate.getRequestId())
                        + ",把静态计算请求放入队列中出现错误");
                log.error("错误原因:", e);
            }
        }
    }

    /**
     * 真正处理静态请求计算信息
     *
     * @Title realHanderStaticCalculateRequest
     * @Description: 真正处理静态请求计算信息
     * @author: caohui
     * @Date: 2018年5月21日/下午1:37:55
     */
    private void realHanderStaticCalculateRequest(StaticCalculateDTO staticCalculate) {
        calculateManageService.handerStaticCalculate(staticCalculate);
    }
}
