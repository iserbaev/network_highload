//package ru.nh.digital_wallet
//
//import cats.Order
//import cats.data.{Chain, NonEmptyChain, OptionT}
//import cats.effect.std.{Queue, UUIDGen}
//import cats.effect.{IO, Resource}
//import cats.syntax.all._
//import fs2.Stream
//import org.typelevel.log4cats.{Logger, LoggerFactory}
//import pureconfig.ConfigReader
//import pureconfig.generic.semiauto.deriveReader
//import ru.nh.digital_wallet.HeartbeatManager.HeartbeatTask
//
//import java.time.Instant
//import java.util.UUID
//import scala.concurrent.duration.FiniteDuration
//
//private[carrier] final class HeartbeatManager(
//                                               val config: Config,
//                                               internalTasks: Queue[IO, HeartbeatTask],
//                                               sign: UUID
//                                             )(implicit log: Logger[IO])
//  extends BatchSupport {
//  import JobAccessor._
//
//  private[job] def releaseExpiredJobs(ttl: FiniteDuration): IO[Unit] =
//    jobManager.accessor
//      .releaseAllExpired(ttl)
//      .flatMap { count =>
//        log
//          .debug(show"Released [$count] jobs after $ttl of inactivity.")
//          .whenA(count > 0)
//      }
//
//  private[job] def releaseHeartbeat: Stream[IO, Unit] =
//    sendInternalTaskSpaced(ReleaseHeartbeat, config.releaseTimeout / 2)
//
//  private[job] def acknowledgementHeartbeat: Stream[IO, Unit] =
//    sendInternalTaskSpaced(AcknowledgementsHeartbeat, config.commandAcknowledgementTimeout / 2)
//
//  private[job] def livenessHeartbeat: Stream[IO, Unit] =
//    sendInternalTaskSpaced(LivenessHeartbeat, config.livenessTimeout / 2)
//
//  private[job] def cancelHeartbeat: Stream[IO, Unit] =
//    sendInternalTaskSpaced(CancelHeartbeat, config.cancelTimeout / 2)
//
//  private[job] def periodicMintSpendHeartbeat: Stream[IO, Unit] =
//    sendInternalTaskSpaced(PeriodicMintSpendHeartbeat, config.periodicMintSpendTimeout / 2)
//
//  private[job] def checkQuotaTokensSufficiencyHeartbeat: Stream[IO, Unit] =
//    sendInternalTaskSpaced(CheckQuotaTokensSufficiencyHeartbeat, config.periodicMintSpendTimeout / 2)
//
//  private[job] def axisBalanceResetHeartbeat: Stream[IO, Unit] =
//    sendInternalTaskSpaced(AxisBalanceResetHeartbeat, config.axisBalanceResetTimeout / 2)
//
//  private def sendInternalTaskSpaced(task: HeartbeatTask, timeout: FiniteDuration): Stream[IO, Unit] =
//    Stream
//      .eval(internalTasks.offer(task))
//      .spaced(timeout, startImmediately = false)
//      .repeat
//      .drain
//
//  private[job] def tasksRunLoop: IO[Unit] =
//    internalTasks.take.flatMap {
//      case ReleaseHeartbeat =>
//        log.debug(s"Run release heartbeat") *>
//          lock.tryLockedExecution(
//            runReleaseHeartbeat
//          )(ReleaseHeartbeat.toString, sign)
//      case LivenessHeartbeat =>
//        log.debug(s"Run liveness heartbeat") *>
//          lock.tryLockedExecution(
//            runLivenessHeartbeat
//          )(LivenessHeartbeat.toString, sign)
//      case AcknowledgementsHeartbeat =>
//        log.debug(s"Run command ack heartbeat") *>
//          lock.tryLockedExecution(
//            runAcknowledgementHeartbeat
//          )(AcknowledgementsHeartbeat.toString, sign)
//      case CancelHeartbeat =>
//        log.debug(s"Run cancel heartbeat") *>
//          lock.tryLockedExecution(
//            runCancelHeartbeat
//          )(CancelHeartbeat.toString, sign)
//      case PeriodicMintSpendHeartbeat =>
//        log.debug(s"Run periodic mint/spend tokens heartbeat") *>
//          lock.tryLockedExecution(
//            runPeriodicMintSpendHeartbeat
//          )(PeriodicMintSpendHeartbeat.toString, sign)
//      case CheckQuotaTokensSufficiencyHeartbeat =>
//        log.debug(s"Run check quota tokens sufficiency heartbeat") *>
//          lock.tryLockedExecution(
//            runCheckQuotaTokensSufficiencyHeartbeat
//          )(CheckQuotaTokensSufficiencyHeartbeat.toString, sign)
//      case AxisBalanceResetHeartbeat =>
//        log.debug(s"Run axis balance reset heartbeat") *>
//          lock.tryLockedExecution(
//            resetForgottenQuotaAxesBalances
//          )(AxisBalanceResetHeartbeat.toString, sign)
//    }.void
//
//  private def runReleaseHeartbeat: IO[Unit] =
//    releaseExpiredJobs(config.releaseTimeout)
//      .handleErrorWith { ex =>
//        log.warn(ex)(show"${ex.getClass.getSimpleName} when releasing stale jobs: ${ex.getMessage}")
//      }
//
//  /** Checks that all sent commands where acknowledged by corresponding workers.
//   *
//   * Check is performed every `commandAcknowledgementTimeout / 2` and command is deemed
//   * unacknowledged if response was not received after `commandAcknowledgementTimeout`.
//   *
//   * If command is unacknowledged then resend first command without acknowledgement
//   * again.
//   */
//  private def runAcknowledgementHeartbeat: IO[Unit] = {
//    def resendCommands(expiredStatus: ForgottenJobs): IO[Unit] =
//      expiredStatus.commands
//        .minimumByOption(_.createdAt)
//        .traverse_(jc =>
//          jobManager.commandEvents
//            .publishRecorded(jc.toRecordedJobCommand, resend = true)
//        )
//
//    getForgottenJobs(config.commandAcknowledgementTimeout, withUnacknowledgedCommands = true)
//      .flatTap(jobs => log.debug(s"Resend commands for ${jobs.length} jobs").whenA(jobs.nonEmpty))
//      .flatMap(_.traverse_(resendCommands))
//      .handleErrorWith { ex =>
//        log.warn(ex)(show"${ex.getClass.getSimpleName} when check command ack jobs: ${ex.getMessage}")
//      }
//  }
//
//  /** Checks that updates for active jobs received in time from workers
//   *
//   * Check is performed every `livenessTimeout / 2` and active jobs is deemed as not
//   * responsive if update was not received after `livenessTimeout`.
//   *
//   * if update was not received then sent command for update.
//   */
//  private def runLivenessHeartbeat: IO[Unit] = {
//    def checkAlive(expiredStatus: NonEmptyChain[ForgottenJobs]): IO[Unit] = {
//      val actions =
//        expiredStatus.map(fj => LogJobActionRequest(fj.jobId, JobAction.Update, JobCommandRequester.Carrier))
//      jobManager.publishActions(actions.toChain)
//    }
//
//    getForgottenJobs(config.livenessTimeout, withUnacknowledgedCommands = false)
//      .flatTap(jobs => log.debug(s"Check alive ${jobs.length} jobs").whenA(jobs.nonEmpty))
//      .flatMap(groupBatchesAndFoldMap(_, config.defaultBatchSize)(checkAlive))
//      .handleErrorWith { ex =>
//        log.warn(ex)(show"${ex.getClass.getSimpleName} when check alive jobs: ${ex.getMessage}")
//      }
//  }
//
//  /** Checks that updates for active jobs received from workers
//   *
//   * Check is performed every `cancelTimeout / 2` and active jobs is deemed as not
//   * responsive if response was not received after `cancelTimeout * 2`.
//   *
//   * if update was not received in time, then sent Cancel command and mark job as
//   * released, if response was not received for both update and cancel commands, then we
//   * automatically set Canceled status ourselves.
//   */
//  private def runCancelHeartbeat: IO[Unit] =
//    getForgottenJobs(config.cancelTimeout, withUnacknowledgedCommands = true)
//      .flatTap(jobs => log.debug(s"Check ${jobs.length} jobs for cancel heartbeat").whenA(jobs.nonEmpty))
//      .map { jobs =>
//        val (commands, updates) = jobs.foldLeft((Chain.empty[CarrierJobId], Chain.empty[CarrierJobId])) {
//          case ((c, u), j) =>
//            collectCancelHeartbeatActions(c, u, j)
//        }
//        CancelHeartbeatActions(commands, updates)
//      }
//      .flatMap { cancelActions =>
//        val sendCommands = cancelActions.releaseAndSendCancelCommands.map(
//          LogJobActionRequest(_, JobAction.Cancel, JobCommandRequester.Carrier)
//        )
//        val sendUpdates = cancelActions.sendCancelledUpdate.map(
//          JobUpdateRequest(_, JobUpdate(JobStatusUpdate.done(JobState.Cancelled), none))
//        )
//
//        sendCancelHeartbeatActions(sendCommands, sendUpdates)
//      }
//      .handleErrorWith { ex =>
//        log.warn(ex)(show"${ex.getClass.getSimpleName} when cancel jobs: ${ex.getMessage}")
//      }
//
//  private def collectCancelHeartbeatActions(
//                                             releaseAndSendCancelCommands: Chain[CarrierJobId],
//                                             sendCancelledUpdates: Chain[CarrierJobId],
//                                             expiredStatus: ForgottenJobs
//                                           ): (Chain[CarrierJobId], Chain[CarrierJobId]) = {
//    val lastCancel =
//      expiredStatus.commands.filter(_.jobAction == JobAction.Cancel).maximumByOption(_.jobActionIndex)
//    val lastUpdate =
//      expiredStatus.commands.filter(_.jobAction == JobAction.Update).maximumByOption(_.jobActionIndex)
//
//    (lastUpdate, lastCancel) match {
//      case (Some(_), None) =>
//        (releaseAndSendCancelCommands :+ expiredStatus.jobId, sendCancelledUpdates)
//      case (Some(_), Some(_)) =>
//        (releaseAndSendCancelCommands, sendCancelledUpdates :+ expiredStatus.jobId)
//      case _ =>
//        (releaseAndSendCancelCommands, sendCancelledUpdates)
//    }
//  }
//
//  private def sendCancelHeartbeatActions(
//                                          sendCommands: Chain[LogJobActionRequest],
//                                          sendUpdates: Chain[JobUpdateRequest]
//                                        ): IO[Unit] =
//    jobManager.publishActions(sendCommands) *>
//      jobManager.addUpdates(sendUpdates) *>
//      (
//        jobManager.accessor.releaseAllExpired(config.cancelTimeout * 2),
//        log.debug(show"Release ${sendCommands.size} forgotten jobs")
//      ).tupled.whenA(sendCommands.nonEmpty)
//
//  private def getForgottenJobs(
//                                timeout: FiniteDuration,
//                                withUnacknowledgedCommands: Boolean
//                              ): IO[Chain[ForgottenJobs]] =
//    jobManager.accessor
//      .getActiveJobsWithExpiredUpdates(timeout, config.activeJobsFetchLimit)
//      .flatMap { jobs =>
//        IO(withUnacknowledgedCommands && jobs.nonEmpty)
//          .ifM(
//            groupBatchesAndFoldMap(Chain.fromSeq(jobs), config.defaultBatchSize)(
//              collectCommands(_, _.minusNanos(timeout.toNanos), withUnacknowledgedCommands)
//            ),
//            IO(Chain.empty[JobCommandLogRow])
//          )
//          .map { commands =>
//            val commandsByJobId = commands.groupBy(_.jobId)
//
//            Chain
//              .fromSeq(jobs)
//              .map(row =>
//                ForgottenJobs(row.jobId, row, commandsByJobId.get(row.jobId).map(_.toChain).getOrElse(Chain.empty))
//              )
//          }
//      }
//      .flatTap(jobs => jobMonitoringService.setForgottenJobs(jobs.length.toInt).whenA(jobs.nonEmpty))
//
//  private def collectCommands(
//                               statuses: NonEmptyChain[JobStatusRow],
//                               to: Instant => Instant,
//                               withUnacknowledgedCommands: Boolean
//                             ): IO[Chain[JobCommandLogRow]] =
//    IO.realTimeInstant
//      .flatMap { now =>
//        jobManager.accessor
//          .getCommandsInterval(
//            statuses.map(_.jobId),
//            statuses.map(_.lastModifiedAt).minimum,
//            to(now),
//            withUnacknowledgedCommands
//          )
//          .map(Chain.fromSeq)
//      }
//
//  private def runPeriodicMintSpendHeartbeat: IO[Unit] =
//    quotaTokens.traverse_ { qts =>
//      jobManager.accessor
//        .getJobsByStates(JobState.going, config.activeJobsFetchLimit)
//        .flatTap(jobs => log.debug(s"Periodic spend/mint for ${jobs.length} jobs").whenA(jobs.nonEmpty))
//        .flatMap { jobs =>
//          groupBatchesAndFoldMap(Chain.fromSeq(jobs), config.defaultBatchSize)(qts.processRunningJobsBalances)
//            .flatMap { activeAxes =>
//              qts.quotaTokensMonitoringService.setActiveAxes(activeAxes.size)
//            } *> jobMonitoringService.setActiveJobs(jobs.length)
//        }
//        .handleErrorWith { ex =>
//          log.warn(ex)(show"${ex.getClass.getSimpleName} when run periodic mint/spend heartbeat: ${ex.getMessage}")
//        }
//        .void
//    }
//
//  private def runCheckQuotaTokensSufficiencyHeartbeat: IO[Unit] = {
//    def lastPauseIsInternal(nec: NonEmptyChain[JobCommandLogRow], jobId: CarrierJobId): Boolean =
//      nec
//        .filter(jc => jc.jobId == jobId && jc.jobAction == JobAction.Pause)
//        .lastOption
//        .exists(_.requester.exists(_ == JobCommandRequester.Carrier))
//
//    def check(runningJobs: NonEmptyChain[JobStatusRow], qts: QuotaTokensService): IO[Chain[CheckQuotaTokensActions]] =
//      (
//        collectCommands(runningJobs, identity, withUnacknowledgedCommands = false),
//        qts.checkBalanceSufficiencyBatch(runningJobs.map(_.jobId))
//      )
//        .mapN { (commandsLog, checkBalanceResults) =>
//          val commandsByJobId: Map[CarrierJobId, NonEmptyChain[JobCommandLogRow]] = commandsLog.groupBy(_.jobId)
//          val jobStatusByJobId: Map[CarrierJobId, JobStatusRow] =
//            runningJobs.toList.groupMapReduce(_.jobId)(r => r)((t, _) => t)
//
//          val (toPause, toResume) = checkBalanceResults
//            .foldLeft((Set.empty[CarrierJobId], Set.empty[CarrierJobId])) {
//              case ((toPause, toResume), checkBalanceResult) =>
//                (checkBalanceResult.isEnoughToProceed, checkBalanceResult.jobIds.flatMap(jobStatusByJobId.get)) match {
//                  case (false, runningJobsOnAxis) =>
//                    val candidatesToPause = runningJobsOnAxis
//                      .filter(js => js.jobState == JobState.Running || js.jobState == JobState.Pending)
//                      .map(_.jobId)
//
//                    (toPause ++ candidatesToPause, toResume)
//                  case (true, runningJobsOnAxis) =>
//                    val candidatesToResume = runningJobsOnAxis
//                      .filter(js => js.jobState == JobState.Paused)
//                      .filter(js => commandsByJobId.get(js.jobId).exists(lastPauseIsInternal(_, js.jobId)))
//                      .map(_.jobId)
//
//                    (toPause, toResume ++ candidatesToResume)
//                }
//
//            }
//
//          Chain.one(CheckQuotaTokensActions(Chain.fromIterableOnce(toPause), Chain.fromIterableOnce(toResume)))
//        }
//
//    quotaTokens.traverse_ { qts =>
//      jobManager.accessor
//        .getJobsByStates(JobState.going, config.activeJobsFetchLimit)
//        .flatTap(jobs => log.debug(s"Check quota tokens sufficiency for ${jobs.length} jobs").whenA(jobs.nonEmpty))
//        .flatMap { jobs =>
//          groupBatchesAndFoldMap(Chain.fromSeq(jobs), config.defaultBatchSize)(check(_, qts)).flatMap { result =>
//            val commands = result.flatMap { r =>
//              r.jobsToPause.map(LogJobActionRequest(_, JobAction.Pause, JobCommandRequester.Carrier)) ++
//                r.jobsToResume.map(LogJobActionRequest(_, JobAction.Resume, JobCommandRequester.Carrier))
//            }
//
//            jobManager.publishActions(commands) <*
//              log
//                .debug(
//                  s"Sent [JobAction.Pause ${commands.count(_.jobAction == JobAction.Pause)}] " ++
//                    s"[JobAction.Resume ${commands.count(_.jobAction == JobAction.Resume)}]  commands"
//                )
//                .whenA(commands.nonEmpty)
//          }
//        }
//        .handleErrorWith { ex =>
//          log.warn(ex)(show"${ex.getClass.getSimpleName} when checks quota tokens sufficiency: ${ex.getMessage}")
//        }
//    }
//  }
//
//  private def resetForgottenQuotaAxesBalances: IO[Unit] =
//    quotaTokens.traverse_ { qt =>
//      qt.balanceAccessor.getActiveQuotaAxes(config.defaultBatchSize).flatTap { axes =>
//        OptionT(NonEmptyChain.fromChain(axes).traverse(qt.rulesAccessor.getQuotaRulesByAxesBatch(_))).semiflatMap {
//          rulesByQuotaAxis =>
//            val jobIds = Chain.fromIterableOnce(rulesByQuotaAxis.flatMap(_._2.map(_.jobId).toIterable))
//
//            def program(ids: NonEmptyChain[CarrierJobId]) = jobManager.accessor
//              .getStatusesBatch(ids)
//              .map { statuses =>
//                rulesByQuotaAxis.collect { case (_, v) =>
//                  v.flatMap(r => Chain.fromOption(statuses.get(r.jobId)).tupleLeft(r))
//                }.toList
//              }
//              .flatMap { axesRules =>
//                axesRules.traverse_ { axisRulesWithJobs =>
//                  axisRulesWithJobs.maximumByOption(_._2.index).traverse_ { case (rule, maxStatus) =>
//                    qt.logOnComplete(rule.jobId, maxStatus.jobState, maxStatus.index, rule, reset = true)
//                      .whenA(axisRulesWithJobs.forall(_._2.jobState.isDone))
//                  }
//                }
//              }
//
//            groupBatchesAndFoldMap(jobIds, config.defaultBatchSize)(program)
//        }.value
//      }
//    }
//}
//
//object HeartbeatManager {
//  import JobAccessor.{ JobCommandLogRow, JobStatusRow }
//
//  final case class Config(
//                           releaseTimeout: FiniteDuration,
//                           defaultBatchSize: Int,
//                           activeJobsFetchLimit: Int,
//                           commandAcknowledgementTimeout: FiniteDuration,
//                           livenessTimeout: FiniteDuration,
//                           cancelTimeout: FiniteDuration,
//                           lockTimeout: FiniteDuration,
//                           periodicMintSpendTimeout: FiniteDuration,
//                           axisBalanceResetTimeout: FiniteDuration
//                         )
//
//  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
//
//  private[job] sealed trait HeartbeatTask
//  private[job] final case object AcknowledgementsHeartbeat extends HeartbeatTask
//  private[job] final case object CancelHeartbeat           extends HeartbeatTask
//  private[job] final case object LivenessHeartbeat         extends HeartbeatTask
//  private[job] final case object ReleaseHeartbeat          extends HeartbeatTask
//
//  private[job] final case object PeriodicMintSpendHeartbeat           extends HeartbeatTask
//  private[job] final case object CheckQuotaTokensSufficiencyHeartbeat extends HeartbeatTask
//  private[job] final case object AxisBalanceResetHeartbeat            extends HeartbeatTask
//
//  private[job] final case class ForgottenJobs(
//                                               jobId: CarrierJobId,
//                                               status: JobStatusRow,
//                                               commands: Chain[JobCommandLogRow]
//                                             )
//
//  implicit val instantOrder: Order[Instant] = Order.fromOrdering[Instant]
//
//  private[job] final case class CancelHeartbeatActions(
//                                                        releaseAndSendCancelCommands: Chain[CarrierJobId],
//                                                        sendCancelledUpdate: Chain[CarrierJobId]
//                                                      )
//
//  private[job] case class CheckQuotaTokensActions(
//                                                   jobsToPause: Chain[CarrierJobId],
//                                                   jobsToResume: Chain[CarrierJobId]
//                                                 )
//
//  def apply(
//             config: Config,
//             jm: JobManager,
//             jobMonitoringService: JobMonitoringService,
//             lockService: LockService,
//             quotaTokens: Option[QuotaTokensService],
//           )(implicit L: LoggerFactory[IO]): Resource[IO, HeartbeatManager] = Resource.suspend {
//    (Queue.unbounded[IO, HeartbeatTask], L.fromClass(classOf[HeartbeatManager]), UUIDGen.randomUUID[IO])
//      .mapN { (tasksQueue, log, sign) =>
//        val heartbeatManager = new HeartbeatManager(
//          config,
//          tasksQueue,
//          jm,
//          quotaTokens,
//          jobMonitoringService,
//          lockService,
//          sign
//        )(log)
//        Resource.eval(log.info(s"Allocating HeartbeatManager with ${jm.accessor.name} store.")) *>
//          startBackgroundDuties(heartbeatManager, quotaTokens.nonEmpty)
//      }
//  }
//
//  private[job] def startBackgroundDuties(
//                                          hm: HeartbeatManager,
//                                          quotaTokensEnabled: Boolean
//                                        ): Resource[IO, HeartbeatManager] =
//    Stream(
//      Stream.eval(hm.tasksRunLoop).repeat,
//      hm.acknowledgementHeartbeat,
//      hm.livenessHeartbeat,
//      hm.releaseHeartbeat,
//      hm.cancelHeartbeat,
//      hm.periodicMintSpendHeartbeat.whenA(quotaTokensEnabled),
//      hm.checkQuotaTokensSufficiencyHeartbeat.whenA(quotaTokensEnabled),
//      hm.axisBalanceResetHeartbeat.whenA(quotaTokensEnabled),
//    ).parJoinUnbounded.compile.drain.background
//      .as(hm)
//
//}
