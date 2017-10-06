/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.calling.controllers

import com.waz.content.GlobalPreferences.AutoAnswerCallPrefKey
import com.waz.model.ConvId
import com.waz.service.call.CallInfo.CallState.OtherCalling
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.common.controllers.{CameraPermission, PermissionsController, RecordAudioPermission}

/**
  * This class is intended to be a relatively small controller that every PermissionsActivity can have access to in order
  * to start and accept calls. This controller requires a PermissionsActivity so that it can request and display the
  * related permissions dialogs, that's why it can't be in the GlobalCallController
  */
class CallPermissionsController(implicit inj: Injector, cxt: WireContext) extends Injectable {

  import Threading.Implicits.Background
  private implicit val eventContext = cxt.eventContext

  val globController = inject[GlobalCallingController]
  import globController._

  val permissionsController = inject[PermissionsController]

  for {
    cId <- convId
    incomingCall <- callState.map {
      case OtherCalling => true
      case _ => false
    }
    autoAnswer <- prefs.flatMap(_.preference(AutoAnswerCallPrefKey).signal)
  } if (incomingCall && autoAnswer) startCall(cId)

  /**
    * Determines if a call is available and either joins that call, or starts a new one. If there is
    * already an ongoing call, the withVideo and variableBitRate flags will be ignored by the
    * underlying service
    */
  def startCall(convId: ConvId, withVideo: Boolean = false): Unit = {
    convIdOpt.head.map(_.isDefined).map { incoming =>
      permissionsController.requiring(if (withVideo) Set(CameraPermission, RecordAudioPermission) else Set(RecordAudioPermission)) {
        callingService.head.map(_.startCall(convId, withVideo))
      }(R.string.calling__cannot_start__title, if (withVideo) R.string.calling__cannot_start__no_video_permission__message else R.string.calling__cannot_start__no_permission__message,
        if (incoming) callingService.head.map(_.endCall(convId)))
    }
  }

}
