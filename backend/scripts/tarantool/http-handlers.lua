local exports = {}

json = require('json')
log = require('log')
datetime = require('datetime')
uuid = require('uuid')

exports.add_conversation = function(req)
    local conversation = req:json()
    log.info("Received conversation")
    log.info(conversation)

    local private_conversation_participant = nil

    if conversation.private_conversation then
        private_conversation_participant = uuid.fromstr(conversation.private_conversation_participant)
    end

    local conversation_id = uuid.new()

    box.space.conversation:insert {
        conversation_id,
        uuid.fromstr(conversation.participant),
        conversation.private_conversation,
        private_conversation_participant,
        datetime.new()
    }

    if conversation.private_conversation then
        box.space.conversation:insert {
            conversation_id,
            private_conversation_participant,
            conversation.private_conversation,
            uuid.fromstr(conversation.participant),
            datetime.new()
        }
    end

    log.info("Conversation saved success")

    local resp = req:render { json = conversation_id }
    resp.status = 201
    return resp
end

exports.get_private_conversation = function(req)
    local participant_id = req:stash('participant_id')
    local private_participant_id = req:stash('private_participant_id')
    log.info("Received request to get_private_conversation")
    log.info(participant_id)
    log.info(private_participant_id)
    local conversation = box.space.conversation.index.participants:select({ uuid.fromstr(participant_id), uuid.fromstr(private_participant_id) })

    local resp = req:render({ json = conversation })
    resp.status = 200
    return resp
end

exports.add_dialog = function(req)
    local dialog = req:json()
    log.info("Received dialog")
    log.info(dialog)

    result, err = pcall(function()
        box.space.private_message_log:insert {
            uuid.fromstr(dialog.conversation_id),
            tonumber(dialog.conversation_index),
            uuid.fromstr(dialog.message_from),
            uuid.fromstr(dialog.message_to),
            dialog.message,
            datetime.new()
        }
    end)

    if err ~= nil then
        log.error(err)
        local resp = req:render { json = err }
        resp.status = 500
        return resp
    end

    log.info("Dialog saved success")
    local resp = req:render { json = '{}' }
    resp.status = 201
    return resp
end

exports.list_dialogs = function(req)
    local conversation_id = req:stash('conversation_id')
    log.info("Received request to list dialogs")
    log.info(conversation_id)
    local dialogs = box.space.private_message_log.index.primary:select(uuid.fromstr(conversation_id))

    local resp = req:render({ json = dialogs })
    resp.status = 200
    return resp
end

exports.all_dialogs = function(req)
    log.info("Received request to list all dialogs")
    local dialogs = box.space.private_message_log.index.primary:select()

    local resp = req:render({ json = dialogs })
    resp.status = 200
    return resp
end

return exports