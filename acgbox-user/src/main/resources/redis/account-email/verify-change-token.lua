-- 发信时只校验，最终换绑时原子校验并删除，防止凭证被重复消费。
local digest = redis.call('HGET', KEYS[1], 'digest')
if not digest or digest ~= ARGV[1] then
    return 0
end
if ARGV[2] == '1' then
    redis.call('DEL', KEYS[1])
end
return 1
