package com.yhfin.risk.calculate.message;


import com.yhfin.risk.common.requests.message.CalculateMessageSynchronizate;
import com.yhfin.risk.common.requests.message.EntryMessageSynchronizate;
import com.yhfin.risk.common.requests.message.MemoryMessageSynchronizate;
import com.yhfin.risk.common.responses.ServerResponse;

/**
 * 接收消息，处理消息
 * 
 * @author youlangta
 * @since 2018-03-20
 */
public interface IMessageService {

	/**
	 * 同步内存消息
	 * 
	 * @param message
	 */
	void messageMeory(MemoryMessageSynchronizate message);

	/**
	 * 内存计算消息
	 *
	 * @param message
	 * @return
	 */
	ServerResponse<CalculateMessageSynchronizate> calculateMessageSynchronizate(CalculateMessageSynchronizate message);


}
