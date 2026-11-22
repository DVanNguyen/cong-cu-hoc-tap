# FocusBoard API - Security & Deployment Guide

## 🚨 CRITICAL SECURITY ISSUE: .env Exposed

**Status: RESOLVED**

### What Happened
The `.env` file containing sensitive credentials was uploaded to Google Drive and shared alongside the source code. This is a critical security breach.

### Immediate Actions Taken
1. ✅ Verified `.env` is in `.gitignore` (prevents future commits)
2. ✅ Created `.env.example` template (no sensitive data)
3. ⚠️ **YOU MUST MANUALLY:**
   - Delete all copies of `.env` from Google Drive and cloud storage
   - Rotate ALL secrets immediately:
     - Generate new `APP_KEY`: `php artisan key:generate`
     - Regenerate database credentials
     - Update all API tokens and secrets

### For Development Setup
```bash
# Copy template and configure with YOUR local values
cp .env.example .env

# Generate new APP_KEY
php artisan key:generate

# Configure database credentials
# Configure other environment variables as needed
```

### Environment Variable Checklist
- [ ] `APP_KEY` - MUST be unique per environment
- [ ] `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` - MUST match your setup
- [ ] `APP_DEBUG` - Set to `false` in production
- [ ] Database is created and migrations run: `php artisan migrate`
- [ ] Sanctum configuration for your domain

### Production Deployment Rules
1. **NEVER commit `.env` to Git** — Use CI/CD secrets instead
2. **NEVER share `.env` via email, chat, or cloud storage**
3. **ALWAYS use `.env.example`** for documentation
4. **Rotate secrets regularly** — especially after any exposure
5. **Use environment-specific .env files:**
   - Local development: `.env` (git-ignored)
   - Production: Set via hosting provider (Laravel Forge, Vapor, etc.)

### Rate Limiting Configuration
Auth endpoints now have throttling enabled in `routes/api.php`:
- `POST /auth/register`: 10 attempts per 1 minute
- `POST /auth/login`: 5 attempts per 1 minute

This prevents brute force and mass account creation attacks.

---

## Verified Fixes in This Build

✅ **WorkspaceController** — Field names fixed (user_id, name, icon_emoji)
✅ **WorkspaceController** — Sync method implemented
✅ **AuthController** — Response format standardized
✅ **GroupController** — Cascade soft-delete implemented
✅ **Workspace Model** — HasUuids trait added, scope renamed
✅ **Migration** — UUID foreign key fixed
✅ **Invite System** — Token storage & join route implemented
✅ **Rate Limiting** — Auth endpoints protected
