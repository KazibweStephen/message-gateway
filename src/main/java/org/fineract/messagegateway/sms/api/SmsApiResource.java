/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.fineract.messagegateway.sms.api;


import org.fineract.messagegateway.constants.MessageGatewayConstants;
import org.fineract.messagegateway.exception.MessageGatewayException;
import org.fineract.messagegateway.sms.data.DeliveryStatusData;
import org.fineract.messagegateway.sms.domain.OutboundMessages;
import org.fineract.messagegateway.sms.domain.SMSBridge;
import org.fineract.messagegateway.sms.exception.ProviderNotDefinedException;
import org.fineract.messagegateway.sms.exception.SMSBridgeNotFoundException;
import org.fineract.messagegateway.sms.providers.Provider;
import org.fineract.messagegateway.sms.providers.impl.telerivet.TelerivetMessageProvider;
import org.fineract.messagegateway.sms.repository.SMSBridgeRepository;
import org.fineract.messagegateway.sms.repository.SmsOutboundMessageRepository;
import org.fineract.messagegateway.sms.service.SMSMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/smsgateway/sms")
public class SmsApiResource {

	//This class sends TRANSACTIONAL & PROMOTIONAL SMS

	private SMSMessageService smsMessageService ;

	private static final Logger logger = LoggerFactory.getLogger(SmsApiResource.class);

	@Autowired
	private TelerivetMessageProvider telerivetMessageProvider;

	@Autowired
	private  SMSBridgeRepository smsBridgeRepository;

	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private SmsOutboundMessageRepository smsOutboundMessageRepository;

	@Autowired
    public SmsApiResource(final SMSMessageService smsMessageService) {
		this.smsMessageService = smsMessageService ;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = {"application/json"}, produces = {"application/json"})
    public ResponseEntity<Void> sendShortMessages(@RequestHeader(MessageGatewayConstants.TENANT_IDENTIFIER_HEADER) final String tenantId,
    		@RequestHeader(MessageGatewayConstants.TENANT_APPKEY_HEADER) final String appKey, 
    		@RequestBody final List<OutboundMessages> payload) {
		logger.info("Payload "+ payload.get(0).getMessage());
    	this.smsMessageService.sendShortMessage(tenantId, appKey, payload);
       return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }
	@RequestMapping(value = "/send",method = RequestMethod.POST, consumes = {"application/json"}, produces = {"application/json"})
	public ResponseEntity<Void> sendShortMessagesToProvider(@RequestHeader(MessageGatewayConstants.TENANT_IDENTIFIER_HEADER) final String tenantId,
												  @RequestHeader(MessageGatewayConstants.TENANT_APPKEY_HEADER) final String appKey,
															@RequestHeader(MessageGatewayConstants.X_ORCHESTRATOR) final String orchestrator,
												  @RequestBody final List<OutboundMessages> payload) {
		logger.info("Payload "+ payload.get(0).getMessage());
		this.smsMessageService.sendShortMessageToProvider(tenantId, appKey, payload,orchestrator);
		return new ResponseEntity<>(HttpStatus.ACCEPTED);
	}
    @RequestMapping(value = "/report", method = RequestMethod.POST, consumes = {"application/json"}, produces = {"application/json"})
    public ResponseEntity<Collection<DeliveryStatusData>> getDeliveryStatus(@RequestHeader(MessageGatewayConstants.TENANT_IDENTIFIER_HEADER) final String tenantId,
    		@RequestHeader(MessageGatewayConstants.TENANT_APPKEY_HEADER) final String appKey,@RequestHeader(MessageGatewayConstants.X_ORCHESTRATOR) final String orchestrator,
    		@RequestBody final Collection<Long> internalIds) throws MessageGatewayException {
    	Collection<DeliveryStatusData> deliveryStatus = this.smsMessageService.getDeliveryStatus(tenantId, appKey, internalIds) ;
		logger.info("From SMS API Resource, successfully fetched the message status");
		for(DeliveryStatusData deliveryStatusData :  deliveryStatus) {
			if (deliveryStatusData.getDeliveryStatus() != 300) {
				logger.info("Delivery status is still pending, fetching message status manually ");
				SMSBridge bridge = smsBridgeRepository.findByIdAndTenantId(deliveryStatusData.getBridgeId(),
						deliveryStatusData.getTenantId());
				Provider provider = null;
				try {
					if (bridge == null) {
						throw new SMSBridgeNotFoundException(deliveryStatusData.getBridgeId());
					}
					logger.info("Finding provider for fetching message status....{}", bridge.getProviderKey());
					provider = (Provider) this.applicationContext.getBean(bridge.getProviderKey());
					if (provider == null)
						throw new ProviderNotDefinedException();
					provider.updateStatusByMessageId(bridge, deliveryStatusData.getExternalId(),orchestrator);
					Collection<Long> id = new ArrayList<Long>();
					id.add(Long.valueOf(deliveryStatusData.getId()));
					Collection<DeliveryStatusData> messageDeliveryStatus = this.smsMessageService.getDeliveryStatus(tenantId, appKey, id);
					deliveryStatus = messageDeliveryStatus;
				} catch (ProviderNotDefinedException e) {
					e.printStackTrace();
				}
			}
		}
		return new ResponseEntity<>(deliveryStatus, HttpStatus.OK);

	}
	@RequestMapping(value = "/details/{internalId}", method = RequestMethod.GET, consumes = {"application/json"}, produces = {"application/json"})
	public ResponseEntity<OutboundMessages> getMessageDetails(@RequestHeader(MessageGatewayConstants.TENANT_IDENTIFIER_HEADER) final String tenantId,
															  @RequestHeader(MessageGatewayConstants.TENANT_APPKEY_HEADER) final String appKey,
															  @PathVariable Long internalId) throws MessageGatewayException {

		OutboundMessages smsMessages = this.smsOutboundMessageRepository.findByInternalId(internalId);
		return new ResponseEntity<>(smsMessages, HttpStatus.OK);
	}
}
