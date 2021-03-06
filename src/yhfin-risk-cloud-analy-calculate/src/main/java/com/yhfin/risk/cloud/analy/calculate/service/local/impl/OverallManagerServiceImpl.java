/****************************************************
 * 创建人：  @author caohui    
 * 创建时间: 2018/5/26/13:27
 * 项目名称: yhfin-risk-cloud-parent 
 * 文件名称: OverallManagerServiceImpl.java
 * 文件描述: @Description 计算分析服务统一管理类服务
 * 公司名称: 深圳市赢和信息技术有限公司
 *
 * All rights Reserved, Designed By 深圳市赢和信息技术有限公司
 * @Copyright:2016-2018
 *
 ********************************************************/
package com.yhfin.risk.cloud.analy.calculate.service.local.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.yhfin.risk.cloud.analy.calculate.service.feign.ISendMessageService;
import com.yhfin.risk.cloud.analy.calculate.service.local.IOverallManagerService;
import com.yhfin.risk.core.analy.manage.IEntryStaticAnalyManageService;
import com.yhfin.risk.core.analy.optimize.IEntryStaticAnalyService;
import com.yhfin.risk.core.calculate.reduce.ICalculateService;
import com.yhfin.risk.core.common.pojos.dtos.analy.FinalStaticEntryCalculateDTO;
import com.yhfin.risk.core.common.pojos.dtos.analy.FinalStaticEntryCalculateResultDTO;
import com.yhfin.risk.core.common.pojos.dtos.notice.StaticSingleFundCalculateDTO;
import com.yhfin.risk.core.common.pojos.dtos.result.ResultHandleResultDTO;
import com.yhfin.risk.core.common.pojos.dtos.synchronizate.EntryMessageSynchronizateDTO;
import com.yhfin.risk.core.common.pojos.dtos.synchronizate.MemoryMessageSynchronizateDTO;
import com.yhfin.risk.core.common.pojos.dtos.synchronizate.SynchronizateTableDataStatusDTO;
import com.yhfin.risk.core.common.utils.StringUtil;
import com.yhfin.risk.core.result.hander.IHanderResultService;
import com.yhfin.risk.core.synchronizate.entry.IEntrySynchronizateService;
import com.yhfin.risk.core.synchronizate.memory.IMemorySynchronizateService;

import lombok.extern.slf4j.Slf4j;

/**
 * 计算分析服务统一管理类服务
 * 包名称：com.yhfin.risk.cloud.analy.calculate.service.local.impl
 * 类名称：OverallManagerServiceImpl
 * 类描述：计算分析服务统一管理类服务
 * 创建人：@author caohui
 * 创建时间：2018/5/26/13:27
 */
@Service
@Slf4j
public class OverallManagerServiceImpl implements IOverallManagerService {

    private String currentSerialNumber;
    @Value("${yhfin.common.isSaveHisResult}")
    private boolean isSaveHisResult;
    @Autowired
    private IMemorySynchronizateService memorySynchronizateService;

    @Autowired
    private IEntrySynchronizateService entrySynchronizateService;

    @Autowired
    private IEntryStaticAnalyService entryStaticAnalyService;

    @Autowired
    private ICalculateService calculateService;

    @Autowired
    private IEntryStaticAnalyManageService entryStaticAnalyManageService;

    @Autowired
    private IHanderResultService handerResultService;

    @Autowired
    private ISendMessageService sendMessageService;

    private ExecutorService handerMessagePool = new ThreadPoolExecutor(1, 1,
            1000L, TimeUnit.MICROSECONDS, new LinkedBlockingQueue<Runnable>(20000),
            (runnable) -> {
                return new Thread(runnable, "分析计算服务，处理同步条目、内存、单个基金计算请求线程池单线程");
            });


    private ExecutorService analyFundExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() + 1,
            1000L, TimeUnit.MICROSECONDS, new LinkedBlockingQueue<Runnable>(20000), new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "分析单个基金线程池,线程" + mCount.getAndIncrement());
        }
    });

    private ExecutorService calculateFundExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2 + 2,
            1000L, TimeUnit.MICROSECONDS, new LinkedBlockingQueue<Runnable>(20000), new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "计算单个基金条目结果线程池,线程" + mCount.getAndIncrement());
        }
    });

    private ExecutorService handerInitialResultPool = new ThreadPoolExecutor(1, 2,
            1000L, TimeUnit.MICROSECONDS, new LinkedBlockingQueue<Runnable>(512), (runnable) -> {
        return new Thread(runnable, "从基金分析完初始结果队列中取初始结果线程池单线程");
    });

    private final Integer STOP_NUMBER = 6;

    private ScheduledExecutorService handerInitialResultPoolSchedule = Executors.newSingleThreadScheduledExecutor();

    private BlockingDeque<Object> messagePoolDeque;
    private BlockingDeque<FinalStaticEntryCalculateResultDTO> calculateResults;

    {
        this.messagePoolDeque = new LinkedBlockingDeque<>(1000);
        this.calculateResults = new LinkedBlockingDeque<>(1000000);
    }

    @PostConstruct
    private void init() {
        if (log.isInfoEnabled()) {
            log.info("定时取结果信息线程启动");
        }
        handerInitialResultPoolSchedule.scheduleAtFixedRate(
                () -> {
                    if (calculateResults.size() > 0) {
                        try {
                            List<FinalStaticEntryCalculateResultDTO> calculateResultDTOs = new ArrayList<>(8000);
                            calculateResults.drainTo(calculateResultDTOs, 4000);
                            handerCalculateResults(calculateResultDTOs);
                        } catch (Exception e) {
                            if (log.isErrorEnabled()) {
                                log.error("分析结果处理失败", e);
                            }
                        }
                    }
                }, 100, 5000, TimeUnit.MILLISECONDS
        );

        handerMessagePool.execute(() -> {
            while (true) {
                try {
                    Object handerMessage = messagePoolDeque.take();
                    if (handerMessage instanceof MemoryMessageSynchronizateDTO) {
                        realHanderMemoryMessageSynchronizate((MemoryMessageSynchronizateDTO) handerMessage);
                    } else if (handerMessage instanceof EntryMessageSynchronizateDTO) {
                        realHanderEntryMessageSynchronizate((EntryMessageSynchronizateDTO) handerMessage);
                    } else if (handerMessage instanceof StaticSingleFundCalculateDTO) {
                        realHanderStaticSingleFundCalculate((StaticSingleFundCalculateDTO) handerMessage);
                    }
                } catch (InterruptedException e) {
                    if (log.isErrorEnabled()) {
                        log.info("分析计算服务，处理同步条目、内存、单个基金计算请求线程池单线程从队列中取出信息出现错误", e);
                    }
                }
            }
        });

        handerInitialResultPool.execute(() -> {
            while (true) {
                FinalStaticEntryCalculateDTO finalStaticEntryCalculate = entryStaticAnalyService
                        .finalStaticEntryCalculateTake();
                if (finalStaticEntryCalculate != null) {
                    CompletableFuture.runAsync(() -> {
                        FinalStaticEntryCalculateResultDTO finalStaticEntryCalculateResult = calculateService.calculateRequest(finalStaticEntryCalculate);
                        try {
                            calculateResults.put(finalStaticEntryCalculateResult);
                        } catch (InterruptedException e) {
                            if (log.isErrorEnabled()) {
                                log.error(StringUtil.commonLogStart(finalStaticEntryCalculateResult.getSerialNumber(), finalStaticEntryCalculateResult.getRequestId())
                                        + "把计算完毕的基金{}条目{}结果信息放入缓存队列中失败", finalStaticEntryCalculateResult.getFundId(), finalStaticEntryCalculateResult.getRiskId());
                                log.error("错误原因:" + e, e);
                            }
                        }
                    }, calculateFundExecutor);
                }
            }
        });


    }


    /**
     * 从队列中拿出同步内存消息 开始同步内存
     *
     * @param message 同步内存消息
     * @Title realHanderMemoryMessageSynchronizate
     * @Description: 从队列中拿出同步内存消息 开始同步内存
     * @author: caohui
     * @Date: 2018/5/26/13:53
     */
    private void realHanderMemoryMessageSynchronizate(MemoryMessageSynchronizateDTO message) {
        try {
            if (message.getSynchrozateAll() != null && message.getSynchrozateAll()) {
                if (message.getDropTable() != null && message.getDropTable()) {
                    memorySynchronizateService.synchronizateAllDropViews();
                    memorySynchronizateService.synchronizateAllDropTables();
                }
                memorySynchronizateService.synchronizateAllTableDatas();
                return;
            } else {
                if (message.getDeleteTableDatas() != null && !message.getDeleteTableDatas().isEmpty()) {
                    for (Map.Entry<String, List<Object[]>> entry : message.getDeleteTableDatas().entrySet()) {
                        memorySynchronizateService.synchronizateDropData(entry.getKey(), entry.getValue());
                    }
                }
                List<String> tableNames = message.getTableNames();
                if (tableNames != null && !tableNames.isEmpty()) {
                    if (message.getDropTable()) {
                        memorySynchronizateService
                                .synchronizateDropTables(tableNames.toArray(new String[tableNames.size()]));
                    }
                    memorySynchronizateService
                            .synchronizateTableDatas(tableNames.toArray(new String[tableNames.size()]));
                }
            }

        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                        + ",同步内存消息处理失败,同步内存版本号{}", message.getMemoryVersionNumber());
                log.error("错误原因:" + e, e);
            }
        }
    }


    /**
     * 把计算出来的结果插入数据库
     *
     * @param calculateResults 计算结果
     * @Title handerCalculateResults
     * @Description: 把计算出来的结果插入数据库
     * @author: caohui
     * @Date: 2018/5/26/14:53
     */
    private void handerCalculateResults(List<FinalStaticEntryCalculateResultDTO> calculateResults) {
        if (calculateResults != null && !calculateResults.isEmpty()) {
            final List<FinalStaticEntryCalculateResultDTO> finalCalculateResults = calculateResults.stream().filter((item) -> {
                return StringUtils.equals(this.currentSerialNumber, item.getSerialNumber());
            }).collect(Collectors.toList());
            if (finalCalculateResults != null && !finalCalculateResults.isEmpty()) {
                String requestId = finalCalculateResults.get(0).getRequestId();
                String serialNumber = finalCalculateResults.get(0).getSerialNumber();
                try {
                    handerResultService.handerResults(finalCalculateResults);
                    sendResultHanderMessage(calculateResults, true);
                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error(StringUtil.commonLogStart(serialNumber, requestId) + ",向数据库插入{}条数据失败," + e.getMessage(),
                                e);
                    }
                    sendResultHanderMessage(calculateResults, false);
                }
            }
        }
    }

    /**
     * 发送结果处理消息给通知中心
     *
     * @param calculateResults 处理的结果
     * @param valid            是否处理成功
     * @Title sendResultHanderMessage
     * @Description: 发送结果处理消息给通知中心
     * @author: caohui
     * @Date: 2018/5/26/15:11
     */
    private void sendResultHanderMessage(List<FinalStaticEntryCalculateResultDTO> calculateResults, boolean valid) {
        if (calculateResults != null && !calculateResults.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("发送计算结果处理信息给消息服务器");
            }
            String requestId = calculateResults.get(0).getRequestId();
            String serialNumber = calculateResults.get(0).getSerialNumber();
            Map<String, List<FinalStaticEntryCalculateResultDTO>> listMap = calculateResults.stream()
                    .collect(Collectors.groupingBy(FinalStaticEntryCalculateResultDTO::getFundId));
            listMap.values().parallelStream().map((item) -> {
                if (item.size() > 0) {
                    ResultHandleResultDTO resultHandleResultDTO = new ResultHandleResultDTO();
                    if (valid) {
                        resultHandleResultDTO.setSuccessResult(new AtomicInteger(item.size()));
                    } else {
                        resultHandleResultDTO.setErrorResult(new AtomicInteger(item.size()));
                    }
                    resultHandleResultDTO.setRequestId(requestId);
                    resultHandleResultDTO.setSerialNumber(serialNumber);
                    resultHandleResultDTO.setFundId(item.get(0).getFundId());
                    return resultHandleResultDTO;
                }
                return null;

            }).filter(item -> item != null).forEach((item) -> {
                if (log.isDebugEnabled()) {
                    log.debug(StringUtil.commonLogStart(serialNumber, requestId) + ",基金{},发送结果处理信息,{}",
                            item.getFundId(), JSON.toJSONString(item));
                }
                if (valid) {
                    entryStaticAnalyManageService.incremmentSuccessResult(item.getFundId(), serialNumber, requestId, item.getSuccessResult().get());
                } else {
                    entryStaticAnalyManageService.incremmentErrorResult(item.getFundId(), serialNumber, requestId, item.getErrorResult().get());
                }
                sendMessageService.resultMessage(item);
            });
        }
    }


    /**
     * 接收同步内存请求
     *
     * @param memoryMessageSynchronizate
     * @Title handerMemoryMessageSynchronizate
     * @Description: 接收同步内存请求
     * @author: caohui
     * @Date: 2018年5月21日/下午4:30:57
     */
    @Override
    public void handerMemoryMessageSynchronizate(MemoryMessageSynchronizateDTO memoryMessageSynchronizate) {
        String serialNumber = memoryMessageSynchronizate.getSerialNumber();
        String requestId = memoryMessageSynchronizate.getRequestId();
        try {
            if (log.isDebugEnabled()) {
                log.debug(StringUtil.commonLogStart(serialNumber, requestId) + ",收到同步内存消息,把消息放入队列中,等待处理");
            }
            messagePoolDeque.put(memoryMessageSynchronizate);
        } catch (InterruptedException e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(serialNumber, requestId) + ",收到同步内存消息,把消息放入队列中,等待处理出现错误", e);
            }
        }
    }

    /**
     * 同队列中取出同步条目信息 同步条目
     *
     * @param message 同步条目信息
     * @Title realHanderEntryMessageSynchronizate
     * @Description: 同队列中取出同步条目信息 同步条目
     * @author: caohui
     * @Date: 2018/5/26/13:56
     */
    private void realHanderEntryMessageSynchronizate(EntryMessageSynchronizateDTO message) {
        try {
            if (message.getSynchronizateAll() != null && message.getSynchronizateAll()) {
                entrySynchronizateService.updateAllEntrys();
                return;
            }
            List<String> deleteRiskIds = message.getDeleteRiskIds();
            if (deleteRiskIds != null && !deleteRiskIds.isEmpty()) {
                entrySynchronizateService.deleteEntrys(deleteRiskIds.toArray(new String[deleteRiskIds.size()]));
            }
            List<String> updateRiskIds = message.getUpdateRiskIds();
            if (updateRiskIds != null && !updateRiskIds.isEmpty()) {
                entrySynchronizateService.updateEntrys(updateRiskIds.toArray(new String[updateRiskIds.size()]));
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(message.getSerialNumber(), message.getRequestId())
                        + ",同步条目消息处理失败,同步条目版本号{}", message.getEntryVersionNumber());
                log.error("错误原因:" + e, e);
            }
        }
    }


    /**
     * 接收同步条目请求
     *
     * @param entryMessageSynchronizate
     * @Title handerEntryMessageSynchronizate
     * @Description: 接收同步条目请求
     * @author: caohui
     * @Date: 2018年5月21日/下午4:31:11
     */
    @Override
    public void handerEntryMessageSynchronizate(EntryMessageSynchronizateDTO entryMessageSynchronizate) {
        String serialNumber = entryMessageSynchronizate.getSerialNumber();
        String requestId = entryMessageSynchronizate.getRequestId();

        try {
            if (log.isDebugEnabled()) {
                log.debug(StringUtil.commonLogStart(serialNumber, requestId) + ",收到同步条目消息,把消息放入队列中,等待处理");
            }
            messagePoolDeque.put(entryMessageSynchronizate);
        } catch (InterruptedException e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(serialNumber, requestId) + ",收到同步条目消息,把消息放入队列中,等待处理出现错误", e);
            }
        }
    }


    /**
     * 从队列中取出单个基金静态请求分析计算信息
     *
     * @param singleFundCalculate 静态请求计算信息
     * @Title realHanderStaticSingleFundCalculate
     * @Description: 从队列中取出单个基金静态请求分析计算信息
     * @author: caohui
     * @Date: 2018/5/26/14:04
     */
    private void realHanderStaticSingleFundCalculate(StaticSingleFundCalculateDTO singleFundCalculate) {
        String serialNumber = singleFundCalculate.getSerialNumber();
        String requestId = singleFundCalculate.getRequestId();
        if (StringUtils.equals(this.currentSerialNumber, serialNumber)) {
            CompletableFuture.runAsync(() -> {
                if (singleFundCalculate.getRiskIds() == null || singleFundCalculate.getFundId().isEmpty()) {
                    entryStaticAnalyService.staticCalculateRequestSingleFund(singleFundCalculate.getFundId(),
                            singleFundCalculate.getRequestId(), singleFundCalculate.getSerialNumber(), null);
                } else {
                    entryStaticAnalyService.staticCalculateRequestSingleFund(singleFundCalculate.getFundId(),
                            singleFundCalculate.getRequestId(), singleFundCalculate.getSerialNumber(),
                            singleFundCalculate.getRiskIds()
                                    .toArray(new String[singleFundCalculate.getRiskIds().size()]));
                }
            }, analyFundExecutor);
        }
    }


    /**
     * 接收静态计算请求
     *
     * @param singleFundCalculate
     * @Title handerStaticSingleFundCalculate
     * @Description: 接收静态计算请求
     * @author: caohui
     * @Date: 2018年5月21日/下午4:31:47
     */
    @Override
    public void handerStaticSingleFundCalculate(StaticSingleFundCalculateDTO singleFundCalculate) {
        String serialNumber = singleFundCalculate.getSerialNumber();
        String requestId = singleFundCalculate.getRequestId();
        try {
            judeValidStaticSingleFundCalculate(serialNumber);
            if (StringUtils.equals(this.currentSerialNumber, serialNumber)) {
                if (log.isDebugEnabled()) {
                    log.debug(StringUtil.commonLogStart(serialNumber, requestId) + ",收到静态基金分析计算消息,把消息放入队列中,等待处理");
                }
                messagePoolDeque.put(singleFundCalculate);
            }
        } catch (InterruptedException e) {
            if (log.isErrorEnabled()) {
                log.error(StringUtil.commonLogStart(serialNumber, requestId) + ",收到静态基金分析计算消息,把消息放入队列中,等待处理出现错误", e);
            }
        }
    }


    /**
     * 判断静态请求是否是有效的静态请求
     *
     * @param serialNumber 流水号
     * @Title judeValidStaticSingleFundCalculate
     * @Description: 判断静态请求是否是有效的静态请求
     * @author: caohui
     * @Date: 2018/5/26/14:00
     */
    private synchronized void judeValidStaticSingleFundCalculate(String serialNumber) {
        if (StringUtils.isBlank(this.currentSerialNumber)) {
            this.currentSerialNumber = serialNumber;
            entryStaticAnalyManageService.getfundAnalyResults().clear();
            handerResultService.initConnection();
            if (!isSaveHisResult) {
                handerResultService.deleteInvalidDatas(serialNumber);
            }
        } else {
            if (!StringUtils.equals(this.currentSerialNumber, serialNumber)) {
                if (Integer.valueOf(this.currentSerialNumber).compareTo(Integer.valueOf(serialNumber)) < 0) {
                    this.currentSerialNumber = serialNumber;
                    entryStaticAnalyManageService.getfundAnalyResults().clear();
                    handerResultService.initConnection();
                    if (!isSaveHisResult) {
                        handerResultService.deleteInvalidDatas(serialNumber);
                    }
                }
            }
        }
    }


    /**
     * 获取当前处理的版本号
     *
     * @Title getCurrentSerialNumber
     * @Description: 获取当前处理的版本号
     * @author: caohui
     * @Date: 2018年5月22日/上午10:18:40
     */
    @Override
    public String getCurrentSerialNumber() {
        return currentSerialNumber;
    }

    /**
     * 获取当前同步状态
     *
     * @Title synchronizateAllDropTables
     * @Description: 获取当前同步状态
     * @author: benguolong
     * @Date: 2018/8/29/14:22
     */
    @Override
    public SynchronizateTableDataStatusDTO getSynchronizateTableDataStatusDTO() {
        return memorySynchronizateService.getSynchronizateTableDataStatusDTO();
    }
}
