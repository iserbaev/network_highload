local exports = {}

exports.test_handler = function(req)
    -- req is a Request object
    -- resp is a Response object
    local resp = req:render({text = req.method..' '..req.path })
    resp.headers['x-test-header'] = 'test';
    resp.status = 201
    return resp
end

return exports