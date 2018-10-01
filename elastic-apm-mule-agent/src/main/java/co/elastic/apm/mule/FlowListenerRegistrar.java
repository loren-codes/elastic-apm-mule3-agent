package co.elastic.apm.mule;

import javax.annotation.PostConstruct;

import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.context.notification.PipelineMessageNotificationListener;
import org.mule.context.notification.NotificationException;
import org.mule.context.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;


@Configuration
public class FlowListenerRegistrar {

	@PostConstruct
	private void registerListeners() throws NotificationException {
		registerPipelineListeners();
	}

	private void registerPipelineListeners() {
		PipelineMessageNotificationListener<PipelineMessageNotification> listener = new PipelineMessageNotificationListener<PipelineMessageNotification>() {

			public void onNotification(PipelineMessageNotification notification) {

				String id = ((MuleEvent) notification.getSource()).getMessage().getMessageRootId();
				String flowName = AnnotatedObjectUtils.getFlowName(notification);
				
				logger.debug(String.format("%s: %s", id, notification.getActionName().toUpperCase().replace(' ', '_')));

				switch (notification.getAction()) {
				case PipelineMessageNotification.PROCESS_START:
					debug(" - PROCESS_START", id, notification);
					
					if (txMap.depth(id) == 0) {
						Transaction transaction = tracer.startTransaction();
						transaction.setName("Flow: " + flowName);
						transaction.withType(Transaction.TYPE_REQUEST);
						transaction.addTag("id", id);
						txMap.put(id, transaction);
					} else {
						AbstractSpan<?> parentSpan = txMap.peek(id);
						Span span = parentSpan.createSpan();
						span.setName("Flow: " + flowName);
						span.withType("flow");
						span.addTag("id", id);
						txMap.put(id, span);
					}
					
					break;

				case PipelineMessageNotification.PROCESS_END:
					debug(" + PROCESS_END", id, notification);
					
					txMap.get(id).end();
					
					break;

				case PipelineMessageNotification.PROCESS_COMPLETE:
					debug(" + PROCESS_COMPLETE", id, notification);					
					break;
				}
			}

		};

		try {
			muleContext.registerListener(listener);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void debug(String message, String id, PipelineMessageNotification notification) {
		if (logger.isDebugEnabled()) {
			String flowName = AnnotatedObjectUtils.getFlowName(notification);
			logger.debug(String.format("%s: %s => flow: %s", id, message, flowName));
		}
	}

	@Autowired
	private MuleContext muleContext;
	
	@Autowired
	private TransactionStackMap txMap;
	
	@Autowired
	private ElasticApmTracer tracer;

	private Logger logger = LoggerFactory.getLogger(FlowListenerRegistrar.class);

}
