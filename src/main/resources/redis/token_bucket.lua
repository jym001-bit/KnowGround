-- Redis TIME is shared across application nodes; both buckets change in one script.
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local totalCap = tonumber(ARGV[1])
local totalRate = tonumber(ARGV[2])
local bgCap = tonumber(ARGV[3])
local bgRate = tonumber(ARGV[4])
local background = ARGV[5] == '1'

local function state(key, capacity, rate)
  local values = redis.call('HMGET', key, 'tokens', 'time')
  local tokens = tonumber(values[1]) or capacity
  local last = tonumber(values[2]) or now
  tokens = math.min(capacity, tokens + math.max(0, now - last) * rate)
  return tokens
end

local total = state(KEYS[1], totalCap, totalRate)
local bg = background and state(KEYS[2], bgCap, bgRate) or 0
local wait = 0
if total < 1 then wait = math.max(wait, math.ceil((1 - total) / totalRate)) end
if background and bg < 1 then wait = math.max(wait, math.ceil((1 - bg) / bgRate)) end
if wait == 0 then
  total = total - 1
  if background then bg = bg - 1 end
end
local function save(key, tokens, capacity, rate)
  redis.call('HSET', key, 'tokens', tokens, 'time', now)
  redis.call('PEXPIRE', key, math.max(1000, math.ceil(2 * capacity / rate)))
end
save(KEYS[1], total, totalCap, totalRate)
if background then save(KEYS[2], bg, bgCap, bgRate) end
return wait
