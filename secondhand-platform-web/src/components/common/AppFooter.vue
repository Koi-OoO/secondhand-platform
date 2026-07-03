<template>
  <footer class="app-footer">
    <div class="app-footer__inner container">
      <div class="app-footer__top">
        <div class="app-footer__brand">
          <span class="footer-logo">校园闲物</span>
          <small>CAMPUS FLEA MARKET · EST. 2026</small>
          <p>把用不上的好物，流转给正需要的人。校园与同城的循环市集。</p>
        </div>

        <div class="app-footer__links">
          <div class="footer-col">
            <h4>逛市集</h4>
            <router-link to="/products">商品广场</router-link>
            <router-link to="/products?sort=latest">最新上架</router-link>
            <router-link to="/products?sort=hottest">人气好物</router-link>
          </div>
          <div class="footer-col">
            <h4>开摊位</h4>
            <router-link to="/product/publish">发布闲置</router-link>
            <router-link to="/user/products">我的商品</router-link>
            <router-link to="/user/orders">我的订单</router-link>
          </div>
          <div class="footer-col">
            <h4>小贴士</h4>
            <span>交易安全</span>
            <span>定价指南</span>
            <span>联系我们</span>
          </div>
        </div>
      </div>

      <div class="app-footer__bottom">
        <span>© 2026 校园闲物 · 让每件好物多一次生命</span>
        <div class="footer-barcode" aria-hidden="true"></div>
      </div>
    </div>
  </footer>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.app-footer {
  margin-top: $space-2xl;
  border-top: $border-ink;
  background: $color-ink;
  color: $color-paper;
  padding: $space-2xl 0 $space-xl;
}

.app-footer__inner {
  display: flex;
  flex-direction: column;
  gap: $space-xl;
}

.app-footer__top {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 1.4fr;
  gap: $space-2xl;
}

.app-footer__brand {
  max-width: 360px;

  small {
    display: block;
    margin-top: 6px;
    color: rgba($color-paper, 0.58);
    font-family: $font-family-mono;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 1.5px;
  }

  p {
    margin-top: $space-md;
    color: rgba($color-paper, 0.72);
    font-size: $font-size-sm;
    line-height: $line-height-relaxed;
  }
}

.footer-logo {
  display: inline-flex;
  color: $color-yellow;
  font-family: $font-family-display;
  font-size: 30px;
  font-weight: $font-weight-heavy;
  letter-spacing: 1px;
}

.app-footer__links {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: $space-lg;
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: 9px;

  h4 {
    margin-bottom: 2px;
    color: $color-yellow;
    font-size: $font-size-sm;
    font-weight: $font-weight-heavy;
  }

  a,
  span {
    color: rgba($color-paper, 0.72);
    font-size: $font-size-sm;
    transition: color $transition-fast, transform $transition-fast;
  }

  a:hover,
  span:hover {
    color: $color-paper;
    transform: translateX(2px);
  }
}

.app-footer__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-lg;
  padding-top: $space-lg;
  border-top: 2px dashed rgba($color-paper, 0.22);
  color: rgba($color-paper, 0.55);
  font-size: $font-size-xs;
}

.footer-barcode {
  width: 148px;
  height: 28px;
  opacity: 0.7;
  background:
    repeating-linear-gradient(
      90deg,
      $color-paper 0 2px,
      transparent 2px 5px,
      $color-paper 5px 6px,
      transparent 6px 12px,
      $color-paper 12px 15px,
      transparent 15px 18px,
      $color-paper 18px 19px,
      transparent 19px 26px
    );
}

@media (max-width: $bp-md) {
  .app-footer__top,
  .app-footer__links {
    grid-template-columns: 1fr;
  }

  .app-footer__bottom {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
