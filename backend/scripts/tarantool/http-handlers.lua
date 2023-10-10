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

    --        { name = 'id', type = 'unsigned' },
    --        { name = 'participant', type = 'unsigned' },
    --        { name = 'private_conversation', type = 'boolean' },
    --        { name = 'private_conversation_participant', type = 'unsigned' },
    --        { name = 'created_at', type = 'datetime' }
    box.space.conversation:insert {
        uuid.new(),
        uuid.fromstr(conversation.participant),
        conversation.private_conversation,
        private_conversation_participant,
        datetime.new()
    }

    log.info("Conversation saved success")

    local resp = req:render { json = json.encode('{}') }
    resp.status = 201
    return resp
end

exports.get_conversation = function(req)
    local conversation_id = req:stash('conversation_id')
    log.info("Received request to list dialogs")
    log.info(conversation_id)
    local dialogs = box.space.private_message_log.index.primary:select(uuid.fromstr(conversation_id))

    local resp = req:render({ json = dialogs })
    resp.status = 200
    return resp
end

exports.add_dialog = function(req)
    local dialog = req:json()
    log.info("Received dialog")
    log.info(dialog)

    box.space.private_message_log:insert {
        uuid.fromstr(dialog.conversation_id),
        tonumber(dialog.conversation_index),
        uuid.fromstr(dialog.message_from),
        uuid.fromstr(dialog.message_to),
        dialog.message,
        datetime.new()
    }
    log.info("Dialog saved success")

    local resp = req:render { json = json.encode('{}')  }
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