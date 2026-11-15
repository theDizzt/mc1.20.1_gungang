local M = {}
local length = 3
local div = 0.5
-- Preserve the pack's pellet spacing; only move the pattern's centre.
local spacing_reference = 16

local function pellet_count(api, original)
    local mag = api:getAttachment("EXTENDED_MAG")
    if mag == "tacz:ammo_mod_he" then return 10 end
    if mag == "tacz:ammo_mod_hp" then return 20 end
    return math.max(1, original)
end

function M.calcSpread(api, count, spread)
    local amount = pellet_count(api, api:getGunIndex():getGunData():getBulletData():getBulletAmount())
    local angle = 0
    if spread == 0 then
        angle = -math.pi / 3
        spread = 5
    end
    -- TaCZ numbers pellets from zero. Five pellets must span +2 .. -2, not +7.5 .. +3.5.
    local offset = (amount - 1) / 2
    local itp = (offset - count) / spacing_reference * spread / 7.5
    local xx = math.cos(angle) * itp * length + (math.random() - 0.5) * div
    local yy = math.sin(angle) * itp * length + (math.random() - 0.5) * div
    return {xx, yy}
end

function M.modify_property(api, id, original)
    if id == "bullet_amount" then return pellet_count(api, original) end
    return original
end

return M
