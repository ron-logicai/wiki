package nl.logicai.wiki.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.repositories.AttachmentRepository;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.PageRepository;
import nl.logicai.wiki.repositories.PageTemplateRepository;
import nl.logicai.wiki.repositories.TagRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Overview for the admin page (spec section 1, role Admin; route /admin). Read-only: it counts what
 * is in the wiki and shows the latest audit events. User and role management lives in UserService.
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

	/** Counts for the summary tiles. */
	public record Overview(long activePages, long trashedPages, long tags, long templates, long attachments,
			long users, long activeUsers) {
	}

	/** An audit event with the page it concerns ({@code page} is null when the page no longer exists). */
	public record AuditRow(AuditEvent event, Page page) {

		/** Dutch label for the action, for the template. */
		public String label() {
			return switch (event.getAction()) {
				case AuditEvent.MOVE -> "Verplaatst";
				case AuditEvent.DELETE -> "Naar prullenbak";
				case AuditEvent.RESTORE -> "Hersteld";
				case AuditEvent.RESTORE_REVISION -> "Revisie teruggezet";
				case UserService.USER_CREATE -> "Gebruiker aangemaakt";
				case UserService.USER_ROLE -> "Rol gewijzigd";
				case UserService.USER_ACTIVATE -> "Gebruiker geactiveerd";
				case UserService.USER_DEACTIVATE -> "Gebruiker gedeactiveerd";
				default -> event.getAction();
			};
		}
	}

	private final PageRepository pages;
	private final TagRepository tags;
	private final PageTemplateRepository templates;
	private final AttachmentRepository attachments;
	private final AuditEventRepository audit;
	private final WikiUserRepository users;

	public AdminService(PageRepository pages, TagRepository tags, PageTemplateRepository templates,
			AttachmentRepository attachments, AuditEventRepository audit, WikiUserRepository users) {
		this.pages = pages;
		this.tags = tags;
		this.templates = templates;
		this.attachments = attachments;
		this.audit = audit;
		this.users = users;
	}

	@PreAuthorize("hasRole('ADMIN')")
	public Overview overview() {
		return new Overview(pages.countByDeletedAtIsNull(), pages.countByDeletedAtIsNotNull(),
				tags.count(), templates.count(), attachments.count(), users.count(), users.countByActiveTrue());
	}

	/** The 20 most recent audit events, newest first. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<AuditRow> recentAudit() {
		List<AuditEvent> events = audit.findTop20ByOrderByOccurredAtDesc();
		List<UUID> ids = events.stream().map(AuditEvent::getPageId).filter(id -> id != null).distinct().toList();
		Map<UUID, Page> byId = pages.findAllById(ids).stream().collect(java.util.stream.Collectors.toMap(Page::getId, Function.identity()));
		return events.stream().map(e -> new AuditRow(e, byId.get(e.getPageId()))).toList();
	}

}
