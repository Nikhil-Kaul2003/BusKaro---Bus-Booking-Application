import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card } from '../components/common';
import { adminApi } from '../api/admin';
import { getApiErrorMessage } from '../api/client';
import './AdminPage.css';
const BUSES_PER_PAGE = 10;
const emptyCategory = { id: null, name: '', description: '' };
const emptyBus = {
    id: null,
    registrationNumber: '',
    model: '',
    manufacturer: '',
    year: new Date().getFullYear(),
    totalSeats: 40,
    status: 'ACTIVE',
    categoryId: '',
};
const makeSeatLayout = (totalSeats, categoryName) => {
    const category = categoryName.toLowerCase();
    const seatsPerRow = category.includes('sleeper') ? 2 : category.includes('luxury') ? 3 : 4;
    const layout = category.includes('sleeper') ? '1+1 sleeper berths' : category.includes('luxury') ? '2+1 premium' : '2+2 non-AC';
    return JSON.stringify({
        rows: Math.ceil(totalSeats / seatsPerRow),
        seatsPerRow,
        layout,
        totalSeats,
        airConditioned: category.includes('luxury') || category.includes('sleeper'),
        categories: { [category.includes('luxury') ? 'PREMIUM' : category.includes('sleeper') ? 'BERTH' : 'STANDARD']: Array.from({ length: category.includes('luxury') ? Math.min(totalSeats, seatsPerRow) : totalSeats }, (_, index) => String(index + 1)) },
    });
};
export const AdminPage = () => {
    const [tab, setTab] = useState('buses');
    const [categories, setCategories] = useState([]);
    const [buses, setBuses] = useState([]);
    const [busPage, setBusPage] = useState(1);
    const [busSearch, setBusSearch] = useState('');
    const [categoryForm, setCategoryForm] = useState(emptyCategory);
    const [busForm, setBusForm] = useState(emptyBus);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');

    // Compare normalized registrations. Exclude the bus being edited, if any.
    // This is an immediate UI check; the backend/database must also enforce uniqueness.
    const normalizedRegistration = busForm.registrationNumber.trim().toUpperCase();
    const duplicateRegistrationBus = normalizedRegistration
        ? buses.find(bus =>
            bus.id !== busForm.id &&
            String(bus.registrationNumber ?? '').trim().toUpperCase() === normalizedRegistration
          )
        : null;

    // Auto-increment IDs are ordered newest-first. Sorting occurs BEFORE pagination.
    const sortedBuses = useMemo(
        () => [...buses].sort((a, b) => Number(b.id) - Number(a.id)),
        [buses]
    );
    // Search all loaded buses, not only the current page. A bus's display name
    // is composed of its manufacturer and model (Bus has no separate name field).
    const filteredBuses = useMemo(() => {
        const query = busSearch.trim().toLowerCase();
        if (!query) return sortedBuses;

        const compactQuery = query.replace(/[\s-]+/g, '');
        return sortedBuses.filter((bus) => {
            const registration = String(bus.registrationNumber ?? '').toLowerCase();
            const manufacturer = String(bus.manufacturer ?? '').toLowerCase();
            const model = String(bus.model ?? '').toLowerCase();
            const busName = `${manufacturer} ${model}`;

            return registration.includes(query)
                || registration.replace(/[\s-]+/g, '').includes(compactQuery)
                || busName.includes(query)
                || manufacturer.includes(query)
                || model.includes(query);
        });
    }, [sortedBuses, busSearch]);

    const totalBusPages = Math.max(1, Math.ceil(filteredBuses.length / BUSES_PER_PAGE));
    const currentBusPage = Math.min(busPage, totalBusPages);
    const pageBuses = useMemo(() => {
        const start = (currentBusPage - 1) * BUSES_PER_PAGE;
        return filteredBuses.slice(start, start + BUSES_PER_PAGE);
    }, [filteredBuses, currentBusPage]);
    const visiblePageStart = Math.max(1, Math.min(currentBusPage - 2, totalBusPages - 4));
    const visiblePageNumbers = Array.from(
        { length: Math.min(5, totalBusPages) },
        (_, index) => visiblePageStart + index
    );
    const loadData = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const [categoryData, busData] = await Promise.all([
                adminApi.getCategories(),
                adminApi.getBuses(),
            ]);
            setCategories(categoryData);
            setBuses(busData);
        }
        catch (loadError) {
            setError(getApiErrorMessage(loadError, 'Could not load the admin data.'));
        }
        finally {
            setLoading(false);
        }
    }, []);
    useEffect(() => { void loadData(); }, [loadData]);
    const saveCategory = async (event) => {
        event.preventDefault();
        setSaving(true);
        setError('');
        setNotice('');
        try {
            const payload = { name: categoryForm.name.trim(), description: categoryForm.description.trim() || null };
            if (categoryForm.id)
                await adminApi.updateCategory(categoryForm.id, payload);
            else
                await adminApi.createCategory(payload);
            setCategoryForm(emptyCategory);
            setNotice('Bus category saved.');
            await loadData();
        }
        catch (saveError) {
            setError(getApiErrorMessage(saveError, 'Could not save this category.'));
        }
        finally {
            setSaving(false);
        }
    };
    const saveBus = async (event) => {
        event.preventDefault();
        if (busForm.categoryId === '')
            return;
        if (duplicateRegistrationBus) {
            setError(`Registration number ${normalizedRegistration} already belongs to another bus.`);
            setNotice('');
            return;
        }
        const isCreatingBus = busForm.id == null;
        setSaving(true);
        setError('');
        setNotice('');
        try {
            const payload = {
                registrationNumber: normalizedRegistration,
                model: busForm.model.trim(),
                manufacturer: busForm.manufacturer.trim(),
                year: Number(busForm.year),
                totalSeats: Number(busForm.totalSeats),
                status: busForm.status,
                categoryId: Number(busForm.categoryId),
                seatLayout: makeSeatLayout(Number(busForm.totalSeats), categories.find(category => category.id === Number(busForm.categoryId))?.name || 'Normal'),
            };
            if (busForm.id)
                await adminApi.updateBus(busForm.id, payload);
            else
                await adminApi.createBus(payload);
            setBusForm(emptyBus);
            setNotice(isCreatingBus ? 'Bus added successfully.' : 'Bus updated successfully.');
            if (isCreatingBus) {
                setBusSearch(''); // Clear any filter so the new bus is visible.
                setBusPage(1); // Reveal newly added bus at the top.
            }
            await loadData();
        }
        catch (saveError) {
            setError(saveError?.response?.status === 409
                ? 'This registration number is already registered. Please use a different number.'
                : getApiErrorMessage(saveError, 'Could not save this bus.'));
        }
        finally {
            setSaving(false);
        }
    };
    const deleteCategory = async (category) => {
        if (!window.confirm(`Delete the “${category.name}” category? Categories used by buses cannot be deleted.`))
            return;
        setError('');
        setNotice('');
        try {
            await adminApi.deleteCategory(category.id);
            if (categoryForm.id === category.id)
                setCategoryForm(emptyCategory);
            setNotice('Bus category deleted.');
            await loadData();
        }
        catch (deleteError) {
            setError(getApiErrorMessage(deleteError, 'Could not delete this category. Reassign its buses first.'));
        }
    };
    const deleteBus = async (bus) => {
        if (!window.confirm(`Delete bus ${bus.registrationNumber}? Buses assigned to trips cannot be deleted.`))
            return;
        setError('');
        setNotice('');
        try {
            await adminApi.deleteBus(bus.id);
            if (busForm.id === bus.id)
                setBusForm(emptyBus);
            setNotice('Bus deleted.');
            await loadData();
        }
        catch (deleteError) {
            setError(getApiErrorMessage(deleteError, 'Could not delete this bus.'));
        }
    };
    const startEditBus = (bus) => {
        setTab('buses');
        setBusForm({
            id: bus.id,
            registrationNumber: bus.registrationNumber,
            model: bus.model,
            manufacturer: bus.manufacturer,
            year: bus.year,
            totalSeats: bus.totalSeats,
            status: bus.status,
            categoryId: bus.categoryId ?? '',
        });
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    return (<div className="admin-page page">
      <header className="admin-page-header">
        <div>
          <span className="admin-eyebrow">BUSKAROO OPERATIONS</span>
          <h1>Admin dashboard</h1>
          <p>Manage fleet records and the categories used to organise buses.</p>
        </div>
        <Button variant="secondary" onClick={() => void loadData()} loading={loading}>Refresh data</Button>
      </header>

      <div className="admin-summary-grid">
        <Card className="admin-stat-card"><span>Fleet buses</span><strong>{buses.length}</strong></Card>
        <Card className="admin-stat-card"><span>Bus categories</span><strong>{categories.length}</strong></Card>
        <Card className="admin-stat-card"><span>Active buses</span><strong>{buses.filter(bus => bus.status === 'ACTIVE').length}</strong></Card>
      </div>

      <div className="admin-tabs" role="tablist" aria-label="Admin resources">
        <button className={tab === 'buses' ? 'active' : ''} onClick={() => setTab('buses')} role="tab" aria-selected={tab === 'buses'}>Buses</button>
        <button className={tab === 'categories' ? 'active' : ''} onClick={() => setTab('categories')} role="tab" aria-selected={tab === 'categories'}>Bus categories</button>
      </div>

      {error && <div className="admin-message error" role="alert">{error}</div>}
      {notice && <div className="admin-message success" role="status">{notice}</div>}

      {tab === 'categories' ? (<div className="admin-content-grid">
          <Card className="admin-form-card">
            <h2>{categoryForm.id ? 'Edit category' : 'Create category'}</h2>
            <form onSubmit={saveCategory} className="admin-form">
              <label>Category name<input required maxLength={80} value={categoryForm.name} onChange={event => setCategoryForm({ ...categoryForm, name: event.target.value })} placeholder="e.g. AC Sleeper"/></label>
              <label>Description<textarea maxLength={250} value={categoryForm.description} onChange={event => setCategoryForm({ ...categoryForm, description: event.target.value })} placeholder="Describe the bus category" rows={3}/></label>
              <div className="admin-form-actions">
                <Button type="submit" loading={saving}>{categoryForm.id ? 'Save changes' : 'Add category'}</Button>
                {categoryForm.id && <Button type="button" variant="secondary" onClick={() => setCategoryForm(emptyCategory)}>Cancel</Button>}
              </div>
            </form>
          </Card>
          <Card className="admin-table-card">
            <div className="admin-table-heading"><h2>Categories</h2><span>{categories.length} total</span></div>
            {loading ? <p className="admin-empty">Loading categories…</p> : categories.length === 0 ? <p className="admin-empty">No categories yet. Create one to start categorising the fleet.</p> : (<div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Name</th><th>Description</th><th>Actions</th></tr></thead><tbody>
                {categories.map(category => <tr key={category.id}><td className="admin-strong">{category.name}</td><td>{category.description || '—'}</td><td><div className="admin-row-actions"><button onClick={() => setCategoryForm({ id: category.id, name: category.name, description: category.description || '' })}>Edit</button><button className="danger" onClick={() => void deleteCategory(category)}>Delete</button></div></td></tr>)}
              </tbody></table></div>)}
          </Card>
        </div>) : (<div className="admin-content-grid">
          <Card className="admin-form-card">
            <h2>{busForm.id ? 'Edit bus' : 'Add a bus'}</h2>
            {categories.length === 0 && <p className="admin-inline-hint">Create a bus category before adding a bus.</p>}
            <form onSubmit={saveBus} className="admin-form">
              <label>
                Registration number
                <input
                  required
                  maxLength={20}
                  value={busForm.registrationNumber}
                  onChange={event => setBusForm({ ...busForm, registrationNumber: event.target.value.toUpperCase() })}
                  placeholder="MH-01-AB-1234"
                  aria-invalid={Boolean(duplicateRegistrationBus)}
                  aria-describedby={duplicateRegistrationBus ? 'registration-duplicate-error' : undefined}
                />
                {duplicateRegistrationBus && (
                  <span id="registration-duplicate-error" className="admin-field-error" role="alert">
                    This registration number is already assigned to another bus.
                  </span>
                )}
              </label>
              <div className="admin-form-row"><label>Manufacturer<input required maxLength={100} value={busForm.manufacturer} onChange={event => setBusForm({ ...busForm, manufacturer: event.target.value })} placeholder="Volvo"/></label><label>Model<input required maxLength={100} value={busForm.model} onChange={event => setBusForm({ ...busForm, model: event.target.value })} placeholder="9600 B8R"/></label></div>
              <div className="admin-form-row"><label>Category<select required value={busForm.categoryId} onChange={event => setBusForm({ ...busForm, categoryId: event.target.value ? Number(event.target.value) : '' })}><option value="">Choose category</option>{categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label><label>Status<select value={busForm.status} onChange={event => setBusForm({ ...busForm, status: event.target.value })}><option value="ACTIVE">Active</option><option value="MAINTENANCE">Maintenance</option><option value="RETIRED">Retired</option></select></label></div>
              <div className="admin-form-row"><label>Year<input type="number" min={2000} max={2035} required value={busForm.year} onChange={event => setBusForm({ ...busForm, year: Number(event.target.value) })}/></label><label>Seats<input type="number" min={1} max={100} required value={busForm.totalSeats} onChange={event => setBusForm({ ...busForm, totalSeats: Number(event.target.value) })}/></label></div>
              <p className="admin-inline-hint">A 2+2 seat layout is generated from the capacity entered above.</p>
              <div className="admin-form-actions"><Button type="submit" loading={saving} disabled={categories.length === 0 || Boolean(duplicateRegistrationBus) || !normalizedRegistration}>{busForm.id ? 'Save changes' : 'Add bus'}</Button>{busForm.id && <Button type="button" variant="secondary" onClick={() => setBusForm(emptyBus)}>Cancel</Button>}</div>
            </form>
          </Card>
          <Card className="admin-table-card">
            <div className="admin-table-heading"><h2>Fleet</h2><span>{buses.length} buses</span></div>

            <div className="admin-fleet-search">
              <label htmlFor="admin-bus-search">Search buses</label>
              <div className="admin-fleet-search-control">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <circle cx="10.8" cy="10.8" r="6.8" />
                  <path d="m16 16 4.5 4.5" />
                </svg>
                <input
                  id="admin-bus-search"
                  type="search"
                  value={busSearch}
                  onChange={(event) => {
                    setBusSearch(event.target.value);
                    setBusPage(1);
                  }}
                  placeholder="Registration no. or bus name (e.g. Volvo 9600)"
                  autoComplete="off"
                />
                {busSearch && (
                  <button type="button" className="admin-fleet-search-clear" onClick={() => { setBusSearch(''); setBusPage(1); }} aria-label="Clear bus search">
                    Clear
                  </button>
                )}
              </div>
              <span className="admin-fleet-search-count" role="status">
                {loading ? 'Loading buses…' : busSearch.trim() ? `${filteredBuses.length} matching ${filteredBuses.length === 1 ? 'bus' : 'buses'}` : 'Search by registration number, manufacturer or model'}
              </span>
            </div>

            {loading ? <p className="admin-empty">Loading fleet…</p> : buses.length === 0 ? <p className="admin-empty">No bus records found.</p> : filteredBuses.length === 0 ? (
              <div className="admin-search-empty">
                <p>No buses found for <strong>“{busSearch.trim()}”</strong>.</p>
                <button type="button" onClick={() => { setBusSearch(''); setBusPage(1); }}>Clear search</button>
              </div>
            ) : (<div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Bus</th><th>Category</th><th>Seats</th><th>Status</th><th>Actions</th></tr></thead><tbody>
                {pageBuses.map(bus => <tr key={bus.id}><td><span className="admin-strong">{bus.registrationNumber}</span><small>{bus.manufacturer} {bus.model} · {bus.year}</small></td><td>{bus.categoryName || 'Uncategorized'}</td><td>{bus.totalSeats}</td><td><span className={`admin-status ${bus.status.toLowerCase()}`}>{bus.status}</span></td><td><div className="admin-row-actions"><button onClick={() => startEditBus(bus)}>Edit</button><button className="danger" onClick={() => void deleteBus(bus)}>Delete</button></div></td></tr>)}
              </tbody></table></div>)}
            {!loading && filteredBuses.length > 0 && (
              <div className="admin-pagination" aria-label="Bus list pagination">
                <span className="admin-pagination-info">
                  Showing {(currentBusPage - 1) * BUSES_PER_PAGE + 1}–{Math.min(currentBusPage * BUSES_PER_PAGE, filteredBuses.length)} of {filteredBuses.length} {busSearch.trim() ? 'matching ' : ''}buses
                </span>
                {totalBusPages > 1 && (
                  <nav className="admin-pagination-controls" aria-label="Fleet pages">
                    <button type="button" onClick={() => setBusPage(1)} disabled={currentBusPage === 1} aria-label="First page">«</button>
                    <button type="button" onClick={() => setBusPage(currentBusPage - 1)} disabled={currentBusPage === 1} aria-label="Previous page">‹</button>
                    {visiblePageNumbers.map(page => (
                      <button
                        key={page}
                        type="button"
                        className={currentBusPage === page ? 'active' : ''}
                        onClick={() => setBusPage(page)}
                        aria-label={`Page ${page}`}
                        aria-current={currentBusPage === page ? 'page' : undefined}
                      >
                        {page}
                      </button>
                    ))}
                    <button type="button" onClick={() => setBusPage(currentBusPage + 1)} disabled={currentBusPage === totalBusPages} aria-label="Next page">›</button>
                    <button type="button" onClick={() => setBusPage(totalBusPages)} disabled={currentBusPage === totalBusPages} aria-label="Last page">»</button>
                  </nav>
                )}
              </div>
            )}
          </Card>
        </div>)}
    </div>);
};
export default AdminPage;
