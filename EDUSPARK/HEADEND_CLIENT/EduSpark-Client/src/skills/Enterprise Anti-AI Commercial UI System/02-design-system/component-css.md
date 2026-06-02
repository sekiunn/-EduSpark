\# 企业级商用组件CSS片段（模型直接复用，无需理解，完整组件库）

\## 1. 通用按钮（企业级极简，无AI塑料感）

/\* 主按钮 \*/

.btn-commercial-primary {

&#x20; padding: 12px 24px;

&#x20; border-radius: 8px;

&#x20; border: none;

&#x20; background: var(--primary-color);

&#x20; color: #fff;

&#x20; box-shadow: 0 2px 8px rgba(0,0,0,0.1);

&#x20; transition: all 0.2s ease;

&#x20; cursor: pointer;

&#x20; font-size: 16px;

&#x20; font-weight: 600;

}

.btn-commercial-primary:active {

&#x20; transform: translateY(1px);

&#x20; box-shadow: 0 1px 4px rgba(0,0,0,0.1);

&#x20; opacity: 0.9;

}



/\* 次要按钮 \*/

.btn-commercial-secondary {

&#x20; padding: 12px 24px;

&#x20; border-radius: 8px;

&#x20; border: 1px solid rgba(0,0,0,0.1);

&#x20; background: transparent;

&#x20; color: var(--text-primary);

&#x20; box-shadow: 0 2px 8px rgba(0,0,0,0.05);

&#x20; transition: all 0.2s ease;

&#x20; cursor: pointer;

&#x20; font-size: 16px;

}

.btn-commercial-secondary:active {

&#x20; transform: translateY(1px);

&#x20; box-shadow: 0 1px 4px rgba(0,0,0,0.05);

}



\## 2. 通用卡片（高级磨砂质感，商用标准）

.card-commercial {

&#x20; border-radius: 12px;

&#x20; background: rgba(255,255,255,0.06);

&#x20; backdrop-filter: blur(10px);

&#x20; box-shadow: 0 4px 12px rgba(0,0,0,0.08);

&#x20; padding: 20px;

&#x20; margin-bottom: 24px;

}

/\* 卡片标题 \*/

.card-commercial-title {

&#x20; font-size: 20px;

&#x20; font-weight: 600;

&#x20; margin-bottom: 16px;

&#x20; color: var(--text-primary);

}

/\* 卡片内容 \*/

.card-commercial-content {

&#x20; font-size: 14px;

&#x20; color: var(--text-secondary);

&#x20; line-height: 1.5;

}



\## 3. 弹窗模态框（企业级极简，无浮夸）

.modal-commercial {

&#x20; position: fixed;

&#x20; top: 50%;

&#x20; left: 50%;

&#x20; transform: translate(-50%, -50%);

&#x20; border-radius: 16px;

&#x20; background: #fff;

&#x20; box-shadow: 0 8px 32px rgba(0,0,0,0.12);

&#x20; padding: 24px;

&#x20; z-index: 9999;

&#x20; min-width: 320px;

&#x20; max-width: 500px;

}

/\* 弹窗遮罩 \*/

.modal-mask {

&#x20; position: fixed;

&#x20; inset: 0;

&#x20; background: rgba(0,0,0,0.4);

&#x20; backdrop-filter: blur(4px);

&#x20; z-index: 9998;

}

/\* 弹窗标题 \*/

.modal-title {

&#x20; font-size: 20px;

&#x20; font-weight: 600;

&#x20; margin-bottom: 16px;

}

/\* 弹窗内容 \*/

.modal-content {

&#x20; font-size: 14px;

&#x20; line-height: 1.5;

&#x20; margin-bottom: 24px;

}

/\* 弹窗按钮组 \*/

.modal-btn-group {

&#x20; display: flex;

&#x20; justify-content: flex-end;

&#x20; gap: 16px;

}



\## 4. 数据表格（中后台专用，商用规范）

.table-commercial {

&#x20; width: 100%;

&#x20; border-collapse: collapse;

&#x20; border-radius: 8px;

&#x20; overflow: hidden;

&#x20; background: #fff;

&#x20; box-shadow: 0 2px 8px rgba(0,0,0,0.05);

}

.table-commercial th {

&#x20; padding: 14px;

&#x20; background: rgba(0,0,0,0.02);

&#x20; font-weight: 600;

&#x20; text-align: left;

&#x20; font-size: 14px;

&#x20; color: var(--text-primary);

&#x20; border-bottom: 1px solid rgba(0,0,0,0.06);

}

.table-commercial td {

&#x20; padding: 14px;

&#x20; border-top: 1px solid rgba(0,0,0,0.06);

&#x20; font-size: 14px;

&#x20; color: var(--text-secondary);

}

.table-commercial tr:hover {

&#x20; background: rgba(0,0,0,0.01);

}



\## 5. 输入框（商用极简，无AI塑料感）

.input-commercial {

&#x20; width: 100%;

&#x20; padding: 12px 16px;

&#x20; border-radius: 8px;

&#x20; border: 1px solid rgba(0,0,0,0.1);

&#x20; background: transparent;

&#x20; font-size: 14px;

&#x20; color: var(--text-primary);

&#x20; box-shadow: 0 1px 4px rgba(0,0,0,0.05);

&#x20; outline: none;

}

.input-commercial:focus {

&#x20; border-color: var(--primary-color);

&#x20; box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);

}



\## 6. 导航栏（企业级商用，适配多端）

.nav-commercial {

&#x20; display: flex;

&#x20; justify-content: space-between;

&#x20; align-items: center;

&#x20; padding: 16px 24px;

&#x20; background: rgba(255,255,255,0.06);

&#x20; backdrop-filter: blur(10px);

&#x20; box-shadow: 0 2px 8px rgba(0,0,0,0.05);

}

.nav-logo {

&#x20; font-size: 20px;

&#x20; font-weight: 600;

&#x20; color: var(--primary-color);

}

.nav-menu {

&#x20; display: flex;

&#x20; gap: 24px;

}

.nav-menu-item {

&#x20; font-size: 14px;

&#x20; color: var(--text-primary);

&#x20; cursor: pointer;

&#x20; transition: color 0.2s ease;

}

.nav-menu-item:hover {

&#x20; color: var(--primary-color);

}



