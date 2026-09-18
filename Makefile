# LeadBrief - local development.
#
# There is no backend any more: the extension calls leads-crm-backend's mounted ai-query-sdk
# instance directly (POST /ai-sdk/query). See README.md.

FRONTEND_DIR := leadlens

.PHONY: install build-extension dev-extension type-check clean

install:
	cd $(FRONTEND_DIR) && npm install

# Emits leadlens/build - load that directory via chrome://extensions > Load unpacked.
build-extension:
	cd $(FRONTEND_DIR) && npm run build

dev-extension:
	cd $(FRONTEND_DIR) && npm run dev:extension

type-check:
	cd $(FRONTEND_DIR) && npm run build

clean:
	rm -rf $(FRONTEND_DIR)/build
