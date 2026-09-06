package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot

/** A pure, feature-local reduction. Runtime ownership remains behind [NmeaRuntimeCommand]. */
data class NmeaInputReduction(
    val localState: NmeaInputLocalState,
    val command: NmeaRuntimeCommand? = null,
    val successNotice: NmeaInputNoticeUi? = null,
)

object NmeaInputLocalReducer {
    fun reduce(
        current: NmeaInputLocalState,
        action: NmeaInputUiAction,
        runtime: NmeaRuntimeSnapshot,
        newConnectionId: () -> ConnectionId,
    ): NmeaInputReduction {
        return when (action) {
        NmeaInputUiAction.AddConnection -> NmeaInputReduction(
            NmeaInputLocalState.Editor(NmeaConnectionDraft.newTcp(newConnectionId())),
        )

        is NmeaInputUiAction.OpenConnection -> if (runtime.hasConnection(action.id)) {
            NmeaInputReduction(NmeaInputLocalState.Detail(action.id))
        } else {
            NmeaInputReduction(current)
        }

        is NmeaInputUiAction.EditConnection -> runtime.connection(action.id)?.let { connection ->
            NmeaInputReduction(NmeaInputLocalState.Editor(NmeaConnectionDraft.from(connection.stored)))
        } ?: NmeaInputReduction(current)

        NmeaInputUiAction.BackToOverview -> NmeaInputReduction(NmeaInputLocalState.Overview)

        is NmeaInputUiAction.ChangeName -> current.updateDraft {
            copy(name = action.value, errors = errors - NmeaInputField.NAME)
        }

        is NmeaInputUiAction.ChangeTransport -> current.updateDraft {
            copy(
                transport = action.value,
                errors = errors - setOf(
                    NmeaInputField.TCP_HOST,
                    NmeaInputField.PORT,
                    NmeaInputField.UDP_SENDER_ADDRESS,
                ),
            )
        }

        is NmeaInputUiAction.ChangeTcpHost -> current.updateDraft {
            copy(tcpHost = action.value, errors = errors - NmeaInputField.TCP_HOST)
        }

        is NmeaInputUiAction.ChangePort -> current.updateDraft {
            copy(portText = action.value, errors = errors - NmeaInputField.PORT)
        }

        is NmeaInputUiAction.ChangeUdpSenderRestriction -> current.updateDraft {
            copy(
                restrictUdpSender = action.enabled,
                errors = if (action.enabled) errors else errors - NmeaInputField.UDP_SENDER_ADDRESS,
            )
        }

        is NmeaInputUiAction.ChangeUdpSenderAddress -> current.updateDraft {
            copy(
                udpSenderAddress = action.value,
                errors = errors - NmeaInputField.UDP_SENDER_ADDRESS,
            )
        }

        is NmeaInputUiAction.ChangeChecksumPolicy -> current.updateDraft {
            copy(checksumPolicy = action.value)
        }

        NmeaInputUiAction.Save -> reduceSave(current, runtime, startAfterSave = false)
        NmeaInputUiAction.SaveAndEnable -> reduceSave(current, runtime, startAfterSave = true)

        NmeaInputUiAction.ConfirmRunningReplacement -> {
            val confirmation = current as? NmeaInputLocalState.ReplacementConfirmation
                ?: return NmeaInputReduction(current)
            val validated = confirmation.draft.validated()
            val config = validated.configOrNull()
                ?: return NmeaInputReduction(NmeaInputLocalState.Editor(validated))
            NmeaInputReduction(
                localState = NmeaInputLocalState.Editor(validated.copy(submitting = true)),
                command = config.toSaveCommand(confirmation.startAfterSave, validated.expectedRevision),
                successNotice = if (confirmation.startAfterSave) {
                    NmeaInputNoticeUi.Started
                } else {
                    NmeaInputNoticeUi.Saved
                },
            )
        }

        NmeaInputUiAction.ConfirmDuplicateTcp -> {
            val confirmation = current as? NmeaInputLocalState.DuplicateTcpConfirmation
                ?: return NmeaInputReduction(current)
            val validated = confirmation.draft.validated()
            val config = validated.configOrNull()
                ?: return NmeaInputReduction(NmeaInputLocalState.Editor(validated))
            NmeaInputReduction(
                localState = NmeaInputLocalState.Editor(validated.copy(submitting = true)),
                command = config.toSaveCommand(
                    startAfterSave = confirmation.startAfterSave,
                    expectedRevision = validated.expectedRevision,
                    duplicateTcpConfirmed = true,
                ),
                successNotice = if (confirmation.startAfterSave) {
                    NmeaInputNoticeUi.Started
                } else {
                    NmeaInputNoticeUi.Saved
                },
            )
        }

        is NmeaInputUiAction.Start -> NmeaInputReduction(
            current,
            NmeaRuntimeCommand.Start(action.id),
            NmeaInputNoticeUi.Started,
        )

        is NmeaInputUiAction.Stop -> NmeaInputReduction(
            current,
            NmeaRuntimeCommand.Stop(action.id),
            NmeaInputNoticeUi.Stopped,
        )

        is NmeaInputUiAction.Retry -> NmeaInputReduction(
            current,
            NmeaRuntimeCommand.Retry(action.id),
            NmeaInputNoticeUi.Started,
        )

        is NmeaInputUiAction.ViewReceivedData -> NmeaInputReduction(current)

        is NmeaInputUiAction.RequestDelete -> if (runtime.hasConnection(action.id)) {
            NmeaInputReduction(NmeaInputLocalState.DeleteConfirmation(action.id))
        } else {
            NmeaInputReduction(current)
        }

        NmeaInputUiAction.ConfirmDelete -> {
            val confirmation = current as? NmeaInputLocalState.DeleteConfirmation
                ?: return NmeaInputReduction(current)
            NmeaInputReduction(
                localState = NmeaInputLocalState.Overview,
                command = NmeaRuntimeCommand.Delete(confirmation.connectionId),
                successNotice = NmeaInputNoticeUi.Deleted,
            )
        }

            NmeaInputUiAction.DismissNotice -> NmeaInputReduction(current)
        }
    }

    private fun reduceSave(
        current: NmeaInputLocalState,
        runtime: NmeaRuntimeSnapshot,
        startAfterSave: Boolean,
    ): NmeaInputReduction {
        val editor = current as? NmeaInputLocalState.Editor ?: return NmeaInputReduction(current)
        val validated = editor.draft.validated()
        val config = validated.configOrNull()
            ?: return NmeaInputReduction(NmeaInputLocalState.Editor(validated))
        val activeSessionWillBeReplaced = validated.endpointChanged &&
            runtime.connection(validated.id)?.token != null
        if (activeSessionWillBeReplaced) {
            return NmeaInputReduction(
                NmeaInputLocalState.ReplacementConfirmation(validated, startAfterSave),
            )
        }
        return NmeaInputReduction(
            localState = NmeaInputLocalState.Editor(validated.copy(submitting = true)),
            command = config.toSaveCommand(startAfterSave, validated.expectedRevision),
            successNotice = if (startAfterSave) NmeaInputNoticeUi.Started else NmeaInputNoticeUi.Saved,
        )
    }
}

private fun NmeaInputLocalState.updateDraft(
    transform: NmeaConnectionDraft.() -> NmeaConnectionDraft,
): NmeaInputReduction {
    val editor = this as? NmeaInputLocalState.Editor ?: return NmeaInputReduction(this)
    return NmeaInputReduction(NmeaInputLocalState.Editor(editor.draft.transform()))
}

private fun NmeaConnectionDraft.validated(): NmeaConnectionDraft {
    val validation = buildMap {
        if (name.isBlank()) put(NmeaInputField.NAME, NmeaInputValidationError.REQUIRED)
        when (transport) {
            NmeaInputTransportKind.TCP_CLIENT -> {
                if (tcpHost.isBlank()) put(NmeaInputField.TCP_HOST, NmeaInputValidationError.REQUIRED)
            }

            NmeaInputTransportKind.UDP_LISTENER -> {
                if (restrictUdpSender && udpSenderAddress.isBlank()) {
                    put(NmeaInputField.UDP_SENDER_ADDRESS, NmeaInputValidationError.REQUIRED)
                }
            }
        }
        if (portText.isEmpty() || portText.any { it !in '0'..'9' }) {
            put(NmeaInputField.PORT, NmeaInputValidationError.ASCII_DIGITS_ONLY)
        } else if (portText.toIntOrNull() !in 1..65_535) {
            put(NmeaInputField.PORT, NmeaInputValidationError.PORT_OUT_OF_RANGE)
        }
    }
    return copy(errors = validation, submitting = false)
}

private fun NmeaConnectionConfig.toSaveCommand(
    startAfterSave: Boolean,
    expectedRevision: Long?,
    duplicateTcpConfirmed: Boolean = false,
): NmeaRuntimeCommand = if (startAfterSave) {
    NmeaRuntimeCommand.SaveAndStart(this, expectedRevision, duplicateTcpConfirmed)
} else {
    NmeaRuntimeCommand.Save(this, expectedRevision, duplicateTcpConfirmed)
}

private fun NmeaRuntimeSnapshot.connection(id: ConnectionId) =
    connections.firstOrNull { it.stored.config.id == id }

private fun NmeaRuntimeSnapshot.hasConnection(id: ConnectionId): Boolean = connection(id) != null
