-- 只删除本次发送失败的验证码，不删除后来生成的验证码。
if redis.call('HGET', KEYS[1], 'digest') == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
