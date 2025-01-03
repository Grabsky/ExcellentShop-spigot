package su.nightexpress.nexshop.shop.impl;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.economybridge.api.Currency;
import su.nightexpress.nexshop.ShopPlugin;
import su.nightexpress.nexshop.api.shop.handler.PluginItemHandler;
import su.nightexpress.nexshop.api.shop.handler.ProductHandler;
import su.nightexpress.nexshop.api.shop.packer.PluginItemPacker;
import su.nightexpress.nexshop.api.shop.packer.ProductPacker;
import su.nightexpress.nexshop.api.shop.product.Product;
import su.nightexpress.nexshop.api.shop.product.VirtualProduct;
import su.nightexpress.nexshop.api.shop.type.TradeType;
import su.nightexpress.nexshop.product.price.AbstractProductPricer;
import su.nightexpress.nexshop.product.price.impl.FlatPricer;
import su.nightexpress.nexshop.shop.virtual.VirtualShopModule;

import java.util.function.UnaryOperator;

public abstract class AbstractProduct<S extends AbstractShop<?>> implements Product {

    protected final ShopPlugin plugin;
    protected final String     id;

    protected S                     shop;
    protected Currency              currency;
    protected ProductHandler        handler;
    protected ProductPacker         packer;
    protected AbstractProductPricer pricer;

    public AbstractProduct(@NotNull ShopPlugin plugin,
                           @NotNull String id,
                           @NotNull S shop,
                           @NotNull Currency currency,
                           @NotNull ProductHandler handler,
                           @NotNull ProductPacker packer) {
        this.plugin = plugin;
        this.id = id.toLowerCase();
        this.shop = shop;
        this.setCurrency(currency);
        this.setPricer(new FlatPricer());
        this.setHandler(handler, packer);
    }

    @NotNull
    protected abstract UnaryOperator<String> replaceExplicitPlaceholders(@Nullable Player player);

    @Override
    @NotNull
    public UnaryOperator<String> replacePlaceholders() {
        return this.replacePlaceholders(null);
    }

    @Override
    @NotNull
    public UnaryOperator<String> replacePlaceholders(@Nullable Player player) {
        var explicit = this.replaceExplicitPlaceholders(player);
        var packer = this.packer.replacePlaceholders();
        var pricer = this.pricer.replacePlaceholders();

        return str -> {
            str = explicit.apply(str);
            str = packer.apply(str);
            str = pricer.apply(str);
            return str;
        };
    }

    @Override
    public boolean isValid() {
        if (this.packer.isDummy()) return false;

        if (this.packer instanceof PluginItemPacker itemPacker && this.handler instanceof PluginItemHandler itemHandler) {
            return itemHandler.isValidId(itemPacker.getItemId());
        }
        return true;
    }

    @Override
    public double getPriceBuy(@NotNull Player player) {
        return this.getPrice(TradeType.BUY, player);
    }

    @Override
    public double getPriceSell(@NotNull Player player) {
        return this.getPrice(TradeType.SELL, player);
    }

    @Override
    public double getPriceSellAll(@NotNull Player player) {
        int amountHas = this.countUnits(player);
        int amountCan = this.getAvailableAmount(player, TradeType.SELL);

        int balance = Math.min((amountCan < 0 ? amountHas : amountCan), amountHas);
        double price = balance * this.getPriceSell(player);

        return Math.max(price, 0);
    }

    @Override
    public double getPrice(@NotNull TradeType tradeType) {
        return this.getPrice(tradeType, null);
    }

    @Override
    public double getPrice(@NotNull TradeType tradeType, @Nullable Player player) {
        double price = this.pricer.getPrice(tradeType);

        if (this instanceof VirtualProduct virtualProduct) {
            if (tradeType == TradeType.BUY && price > 0 && virtualProduct.isDiscountAllowed()) {
                price *= virtualProduct.getShop().getDiscountModifier();
            }
            if (tradeType == TradeType.SELL) {
                if (player != null) {
                    double sellModifier = VirtualShopModule.getSellMultiplier(player);
                    price *= sellModifier;
                }
            }
        }

        return this.currency.fineValue(price);
    }

    @Override
    public void setPrice(@NotNull TradeType tradeType, double price) {
        this.pricer.setPrice(tradeType, this.currency.fineValue(price));
    }


    @Override
    public boolean isTradeable(@NotNull TradeType tradeType) {
        return tradeType == TradeType.BUY ? this.isBuyable() : this.isSellable();
    }

    @Override
    public boolean isBuyable() {
        return this.pricer.getBuyPrice() >= 0D;
    }

    @Override
    public boolean isSellable() {
        double sellPrice = this.pricer.getSellPrice();
        if (sellPrice < 0D) {
            return false;
        }

        // Don't allow to sell items with sell price greater than buy one.
        if (this.shop.isTransactionEnabled(TradeType.BUY) && this.isBuyable()) {
            return sellPrice <= this.pricer.getBuyPrice();
        }

        return true;
    }


    @Override
    public int getUnitAmount() {
        return this.packer.getUnitAmount();
    }

    @Override
    public void delivery(@NotNull Player player, int count) {
        this.delivery(player.getInventory(), count);
    }

    @Override
    public void delivery(@NotNull Inventory inventory, int count) {
        this.packer.delivery(inventory, count);
    }

    @Override
    public void take(@NotNull Player player, int count) {
        this.take(player.getInventory(), count);
    }

    @Override
    public void take(@NotNull Inventory inventory, int count) {
        this.packer.take(inventory, count);
    }

    @Override
    public int count(@NotNull Player player) {
        return this.count(player.getInventory());
    }

    @Override
    public int countUnits(@NotNull Player player) {
        return this.countUnits(player.getInventory());
    }

    @Override
    public int countUnits(@NotNull Inventory inventory) {
        return this.count(inventory) / this.getUnitAmount();
    }

    @Override
    public int count(@NotNull Inventory inventory) {
        return this.packer.count(inventory);
    }

    @Override
    public int countSpace(@NotNull Player player) {
        return this.countSpace(player.getInventory());
    }

    @Override
    public int countSpace(@NotNull Inventory inventory) {
        return this.packer.countSpace(inventory);
    }

    @Override
    public boolean hasSpace(@NotNull Player player) {
        return this.hasSpace(player.getInventory());
    }

    @Override
    public boolean hasSpace(@NotNull Inventory inventory) {
        return this.packer.hasSpace(inventory);
    }



    @Override
    @NotNull
    public S getShop() {
        return this.shop;
    }

    @Override
    @NotNull
    public String getId() {
        return this.id;
    }

    @Override
    @NotNull
    public ItemStack getPreview() {
        return this.packer.getPreview();
    }

    @NotNull
    @Override
    public ProductHandler getHandler() {
        return this.handler;
    }

    @Override
    public void setHandler(@NotNull ProductHandler handler, @NotNull ProductPacker packer) {
        this.handler = handler;
        this.packer = packer;
    }

    @NotNull
    @Override
    public ProductPacker getPacker() {
        return this.packer;
    }

    @Override
    @NotNull
    public AbstractProductPricer getPricer() {
        return this.pricer;
    }

    @Override
    public void setPricer(@NotNull AbstractProductPricer pricer) {
        this.pricer = pricer;
    }

    @Override
    @NotNull
    public Currency getCurrency() {
        return this.currency;
    }

    @Override
    public void setCurrency(@NotNull Currency currency) {
        this.currency = currency;
    }
}
