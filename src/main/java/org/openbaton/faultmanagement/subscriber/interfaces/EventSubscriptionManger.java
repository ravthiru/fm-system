/*
 * Copyright (c) 2015-2016 Fraunhofer FOKUS
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openbaton.faultmanagement.subscriber.interfaces;

import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.sdk.api.exception.SDKException;

/**
 * Created by mob on 13.05.16.
 */
public interface EventSubscriptionManger {
  String subscribe(NetworkServiceRecord networkServiceRecord, Action action) throws SDKException;

  String subscribe(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, Action action)
      throws SDKException;

  void unSubscribe(String subscriptionId) throws SDKException;
}
